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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonarrClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val credentials: ArrCredentialStore,
) {
    suspend fun addSeries(tmdbId: Long, title: String): WatchlistRequestResult {
        if (!credentials.hasSonarr()) {
            return WatchlistRequestResult(false, "Sonarr is not configured in Settings")
        }
        return runCatching {
            val baseUrl = normalizeBaseUrl(credentials.sonarrUrl)
            val apiKey = credentials.sonarrApiKey
            if (seriesExists(baseUrl, apiKey, tmdbId)) {
                return@runCatching WatchlistRequestResult(
                    success = true,
                    message = "$title is already in Sonarr",
                    alreadyRequested = true,
                )
            }
            val lookup = lookupSeries(baseUrl, apiKey, tmdbId, title)
                ?: return@runCatching WatchlistRequestResult(
                    false,
                    "Sonarr could not find $title (TMDB $tmdbId)",
                )
            val rootFolder = fetchRootFolders(baseUrl, apiKey).firstOrNull()?.path
                ?: return@runCatching WatchlistRequestResult(false, "Sonarr has no root folder configured")
            val qualityProfileId = fetchQualityProfiles(baseUrl, apiKey).firstOrNull()?.id
                ?: return@runCatching WatchlistRequestResult(false, "Sonarr has no quality profile configured")

            lookup.addProperty("monitored", true)
            lookup.addProperty("rootFolderPath", rootFolder)
            lookup.addProperty("qualityProfileId", qualityProfileId)
            lookup.addProperty("seasonFolder", true)
            lookup.add("addOptions", JsonObject().apply {
                addProperty("searchForMissingEpisodes", true)
            })

            val body = gson.toJson(lookup)
            val request = Request.Builder()
                .url("$baseUrl/api/v3/series")
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> WatchlistRequestResult(
                        success = true,
                        message = "Added $title to Sonarr — download starting",
                    )
                    response.code == 400 -> {
                        val errorBody = response.body?.string().orEmpty()
                        if (errorBody.contains("already", ignoreCase = true)) {
                            WatchlistRequestResult(true, "$title is already in Sonarr", alreadyRequested = true)
                        } else {
                            WatchlistRequestResult(false, "Sonarr rejected request (${response.code})")
                        }
                    }
                    else -> WatchlistRequestResult(false, "Sonarr error (${response.code})")
                }
            }
        }.getOrElse { error ->
            WatchlistRequestResult(false, error.message ?: "Sonarr request failed")
        }
    }

    suspend fun testConnection(): Result<Unit> {
        if (!credentials.hasSonarr()) return Result.error("Sonarr URL and API key required")
        return runCatching {
            val baseUrl = normalizeBaseUrl(credentials.sonarrUrl)
            val request = Request.Builder()
                .url("$baseUrl/api/v3/system/status")
                .header("X-Api-Key", credentials.sonarrApiKey)
                .get()
                .build()
            okHttpClient.newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.error("Sonarr HTTP ${response.code}")
                }
        }.getOrElse { Result.error(it.message ?: "Sonarr connection failed", it) }
    }

    private fun seriesExists(baseUrl: String, apiKey: String, tmdbId: Long): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/series")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string().orEmpty()
            val series = gson.fromJson<List<JsonObject>>(body, object : TypeToken<List<JsonObject>>() {}.type)
            return series.any { entry ->
                entry.get("tmdbId")?.asLong == tmdbId
            }
        }
    }

    private fun lookupSeries(baseUrl: String, apiKey: String, tmdbId: Long, title: String): JsonObject? {
        val term = URLEncoder.encode(title, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$baseUrl/api/v3/series/lookup?term=$term")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val candidates = gson.fromJson<List<JsonObject>>(body, object : TypeToken<List<JsonObject>>() {}.type)
            return candidates.firstOrNull { it.get("tmdbId")?.asLong == tmdbId }
                ?: candidates.firstOrNull {
                    it.get("title")?.asString?.equals(title, ignoreCase = true) == true
                }
        }
    }

    private fun fetchRootFolders(baseUrl: String, apiKey: String): List<SonarrRootFolder> {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/rootfolder")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, object : TypeToken<List<SonarrRootFolder>>() {}.type) ?: emptyList()
        }
    }

    private fun fetchQualityProfiles(baseUrl: String, apiKey: String): List<SonarrQualityProfile> {
        val request = Request.Builder()
            .url("$baseUrl/api/v3/qualityprofile")
            .header("X-Api-Key", apiKey)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, object : TypeToken<List<SonarrQualityProfile>>() {}.type) ?: emptyList()
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
    }

    private data class SonarrRootFolder(@SerializedName("path") val path: String? = null)
    private data class SonarrQualityProfile(@SerializedName("id") val id: Int? = null)

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
