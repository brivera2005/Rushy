package com.rushy.app

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TraktApiClient(
    private val settings: TraktSettings,
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTrendingMovies(limit: Int = 20): List<TmdbMediaItem> = withContext(Dispatchers.IO) {
        fetchTrendingList("/movies/trending?limit=$limit&extended=full") { body ->
            val type = object : TypeToken<List<TraktTrendingMovieEntry>>() {}.type
            val entries: List<TraktTrendingMovieEntry> = gson.fromJson(body, type)
            entries.mapNotNull { entry -> entry.movie?.toMediaItem("movie") }
        }
    }

    suspend fun fetchTrendingShows(limit: Int = 20): List<TmdbMediaItem> = withContext(Dispatchers.IO) {
        fetchTrendingList("/shows/trending?limit=$limit&extended=full") { body ->
            val type = object : TypeToken<List<TraktTrendingShowEntry>>() {}.type
            val entries: List<TraktTrendingShowEntry> = gson.fromJson(body, type)
            entries.mapNotNull { entry -> entry.show?.toMediaItem("tv") }
        }
    }

    suspend fun fetchPopularMovies(limit: Int = 20): List<TmdbMediaItem> = withContext(Dispatchers.IO) {
        fetchTrendingList("/movies/popular?limit=$limit&extended=full") { body ->
            val type = object : TypeToken<List<TraktMovie>>() {}.type
            val movies: List<TraktMovie> = gson.fromJson(body, type)
            movies.map { it.toMediaItem("movie") }
        }
    }

    suspend fun fetchPopularShows(limit: Int = 20): List<TmdbMediaItem> = withContext(Dispatchers.IO) {
        fetchTrendingList("/shows/popular?limit=$limit&extended=full") { body ->
            val type = object : TypeToken<List<TraktShow>>() {}.type
            val shows: List<TraktShow> = gson.fromJson(body, type)
            shows.map { it.toMediaItem("tv") }
        }
    }

    suspend fun startDeviceAuth(): Result<TraktDeviceAuthSession> = withContext(Dispatchers.IO) {
        val clientId = settings.effectiveClientId()
        if (clientId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Add TRAKT_CLIENT_ID to local.properties"))
        }
        try {
            val body = gson.toJson(mapOf("client_id" to clientId))
            val request = Request.Builder()
                .url("$BASE_URL/oauth/device/code")
                .post(body.toRequestBody(JSON_MEDIA))
                .header("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Trakt device code failed (${response.code}): $responseBody"),
                )
            }
            val parsed = gson.fromJson(responseBody, TraktDeviceCodeResponse::class.java)
            val session = TraktDeviceAuthSession(
                userCode = parsed.userCode,
                verificationUrl = parsed.verificationUrl,
                deviceCode = parsed.deviceCode,
                expiresAtMs = System.currentTimeMillis() + parsed.expiresIn * 1000L,
                pollIntervalSec = parsed.interval.coerceAtLeast(5),
            )
            settings.savePendingDeviceAuth(session)
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "startDeviceAuth failed", e)
            Result.failure(e)
        }
    }

    suspend fun pollDeviceTokenOnce(): TraktPollResult = withContext(Dispatchers.IO) {
        val clientId = settings.effectiveClientId()
        val clientSecret = settings.effectiveClientSecret()
        val deviceCode = settings.pendingDeviceCode
        if (clientId.isBlank() || clientSecret.isBlank() || deviceCode.isBlank()) {
            return@withContext TraktPollResult.Error("Missing Trakt client credentials or device code")
        }
        if (!settings.hasPendingDeviceAuth()) {
            return@withContext TraktPollResult.Expired
        }
        try {
            val body = gson.toJson(
                mapOf(
                    "code" to deviceCode,
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                ),
            )
            val request = Request.Builder()
                .url("$BASE_URL/oauth/device/token")
                .post(body.toRequestBody(JSON_MEDIA))
                .header("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val token = gson.fromJson(responseBody, TraktTokenResponse::class.java)
                settings.saveTokens(token.accessToken, token.refreshToken)
                return@withContext TraktPollResult.Success
            }
            val error = runCatching { gson.fromJson(responseBody, TraktOAuthError::class.java) }.getOrNull()
            when (error?.error) {
                "authorization_pending", "slow_down" -> TraktPollResult.Pending
                "expired_token" -> {
                    settings.clearPendingDeviceAuth()
                    TraktPollResult.Expired
                }
                else -> TraktPollResult.Error(error?.errorDescription ?: error?.error ?: "Token poll failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollDeviceTokenOnce failed", e)
            TraktPollResult.Error(e.message ?: "Token poll failed")
        }
    }

    suspend fun pollDeviceTokenUntilComplete(
        maxWaitMs: Long = 10 * 60 * 1000L,
    ): TraktPollResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < maxWaitMs) {
            when (val result = pollDeviceTokenOnce()) {
                TraktPollResult.Success, is TraktPollResult.Error, TraktPollResult.Expired -> return@withContext result
                TraktPollResult.Pending -> delay(settings.pollIntervalSec * 1000L)
            }
        }
        TraktPollResult.Error("Timed out waiting for Trakt authorization")
    }

    private inline fun fetchTrendingList(path: String, parse: (String) -> List<TmdbMediaItem>): List<TmdbMediaItem> {
        val clientId = settings.effectiveClientId()
        if (clientId.isBlank()) return emptyList()
        val request = traktRequest("$BASE_URL$path", clientId)
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Trakt $path failed: ${response.code}")
            return emptyList()
        }
        val body = response.body?.string() ?: return emptyList()
        return parse(body)
    }

    private fun traktRequest(url: String, clientId: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)
        val token = settings.accessToken
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.get().build()
    }

    private fun TraktMovie.toMediaItem(mediaType: String): TmdbMediaItem = TmdbMediaItem(
        id = ids.tmdb ?: ids.trakt,
        title = title,
        posterPath = images?.poster?.firstOrNull(),
        backdropPath = images?.fanart?.firstOrNull(),
        voteAverage = rating,
        overview = overview,
        mediaType = mediaType,
    )

    private fun TraktShow.toMediaItem(mediaType: String): TmdbMediaItem = TmdbMediaItem(
        id = ids.tmdb ?: ids.trakt,
        name = title,
        posterPath = images?.poster?.firstOrNull(),
        backdropPath = images?.fanart?.firstOrNull(),
        voteAverage = rating,
        overview = overview,
        mediaType = mediaType,
    )

    companion object {
        private const val TAG = "TraktApiClient"
        private const val BASE_URL = "https://api.trakt.tv"
        private val JSON_MEDIA = "application/json".toMediaType()

        fun getInstance(context: android.content.Context): TraktApiClient {
            return TraktApiClient(TraktSettings.getInstance(context))
        }
    }
}

sealed class TraktPollResult {
    data object Success : TraktPollResult()
    data object Pending : TraktPollResult()
    data object Expired : TraktPollResult()
    data class Error(val message: String) : TraktPollResult()
}
