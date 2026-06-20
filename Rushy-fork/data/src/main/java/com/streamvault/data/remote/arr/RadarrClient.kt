package com.streamvault.data.remote.arr

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.streamvault.domain.model.Result
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WatchlistRequestResult(
    val success: Boolean,
    val message: String,
    val alreadyRequested: Boolean = false,
)

@Singleton
class RadarrClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val credentials: ArrCredentialStore,
) {
    suspend fun addMovie(tmdbId: Long, title: String): WatchlistRequestResult {
        if (!credentials.hasRadarr()) {
            return WatchlistRequestResult(false, "Radarr is not configured in Settings")
        }
        return runCatching {
            val baseUrl = normalizeBaseUrl(credentials.radarrUrl)
            val apiKey = credentials.radarrApiKey
            if (movieExists(baseUrl, apiKey, tmdbId)) {
                return@runCatching WatchlistRequestResult(
                    success = true,
                    message = "$title is already in Radarr",
                    alreadyRequested = true,
                )
            }
            val lookup = lookupMovie(baseUrl, apiKey, tmdbId)
                ?: return@runCatching WatchlistRequestResult(
                    false,
                    "Radarr could not find $title (TMDB $tmdbId)",
                )
            val rootFolder = fetchRootFolders(baseUrl, apiKey).firstOrNull()?.path
                ?: return@runCatching WatchlistRequestResult(false, "Radarr has no root folder configured")
            val qualityProfileId = fetchQualityProfiles(baseUrl, apiKey).firstOrNull()?.id
                ?: return@runCatching WatchlistRequestResult(false, "Radarr has no quality profile configured")

            lookup.addProperty("monitored", true)
            lookup.addProperty("rootFolderPath", rootFolder)
            lookup.addProperty("qualityProfileId", qualityProfileId)
            lookup.add("addOptions", JsonObject().apply {
                addProperty("searchForMovie", true)
            })

            val body = gson.toJson(lookup)
            val request = Request.Builder()
                .url("$baseUrl/api/v3/movie")
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> WatchlistRequestResult(
                        success = true,
                        message = "Added $title to Radarr — download starting",
                    )
                    response.code == 400 -> {
                        val errorBody = response.body?.string().orEmpty()
                        if (errorBody.contains("already", ignoreCase = true)) {
                            WatchlistRequestResult(true, "$title is already in Radarr", alreadyRequested = true)
                        } else {
                            WatchlistRequestResult(false, "Radarr rejected request (${response.code})")
                        }
                    }
                    else -> WatchlistRequestResult(false, "Radarr error (${response.code})")
                }
            }
        }.getOrElse { error ->
            WatchlistRequestResult(false, error.message ?: "Radarr request failed")
        }
    }

    suspend fun testConnection(): Result<Unit> {
        if (!credentials.hasRadarr()) return Result.error("Radarr URL and API key required")
        return runCatching {
            val baseUrl = normalizeBaseUrl(credentials.radarrUrl)
            val request = Request.Builder()
                .url("$baseUrl/api/v3/system/status")
                .header("X-Api-Key", credentials.radarrApiKey)
                .get()
                .build()
            okHttpClient.newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.error("Radarr HTTP ${response.code}")
                }
        }.getOrElse { Result.error(it.message ?: "Radarr connection failed", it) }
    }

    private fun movieExists(baseUrl: String, apiKey: String, tmdbId: Long): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/movie?tmdbId=$tmdbId")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string().orEmpty()
            val movies = gson.fromJson<List<JsonObject>>(body, object : TypeToken<List<JsonObject>>() {}.type)
            return movies.isNotEmpty()
        }
    }

    private fun lookupMovie(baseUrl: String, apiKey: String, tmdbId: Long): JsonObject? {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/movie/lookup/tmdb?tmdbId=$tmdbId")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, JsonObject::class.java)
        }
    }

    private fun fetchRootFolders(baseUrl: String, apiKey: String): List<RadarrRootFolder> {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/rootfolder")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, object : TypeToken<List<RadarrRootFolder>>() {}.type) ?: emptyList()
        }
    }

    private fun fetchQualityProfiles(baseUrl: String, apiKey: String): List<RadarrQualityProfile> {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/qualityprofile")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, object : TypeToken<List<RadarrQualityProfile>>() {}.type) ?: emptyList()
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
    }

    private data class RadarrRootFolder(@SerializedName("path") val path: String? = null)
    private data class RadarrQualityProfile(@SerializedName("id") val id: Int? = null)

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
