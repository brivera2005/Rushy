package com.rushy.app

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PlexWatchlistResult(
    val success: Boolean,
    val message: String,
    val alreadyOnWatchlist: Boolean = false,
)

data class PlexDiscoverMetadata(
    @SerializedName("ratingKey") val ratingKey: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("Guid") val guids: List<PlexGuid>? = null,
)

data class PlexGuid(
    @SerializedName("id") val id: String? = null,
)

data class PlexDiscoverContainer<T>(
    @SerializedName("MediaContainer") val mediaContainer: T? = null,
)

data class PlexWatchlistMetadataList(
    @SerializedName("Metadata") val metadata: List<PlexDiscoverMetadata>? = null,
)

data class PlexSearchResultList(
    @SerializedName("SearchResult") val searchResults: List<PlexSearchHit>? = null,
)

data class PlexSearchHit(
    @SerializedName("Metadata") val metadata: PlexDiscoverMetadata? = null,
)

class PlexWatchlistClient(
    private val token: String,
    private val serverUrl: String? = null,
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        runCatching {
            val request = plexRequest("$DISCOVER_BASE/library/sections/watchlist/all?X-Plex-Container-Size=1")
            client.newCall(request).execute().use { it.isSuccessful || it.code == 304 }
        }.getOrDefault(false)
    }

    suspend fun fetchWatchlistTmdbIds(): Set<Int> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext emptySet()
        try {
            val request = plexRequest(
                "$DISCOVER_BASE/library/sections/watchlist/all?includeGuids=1&X-Plex-Container-Size=200",
            )
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptySet()
            val body = response.body?.string() ?: return@withContext emptySet()
            val container = gson.fromJson(body, PlexDiscoverContainer::class.java)
            @Suppress("UNCHECKED_CAST")
            val metadata = (container.mediaContainer as? Map<*, *>)?.get("Metadata") as? List<Map<*, *>>
                ?: return@withContext emptySet()
            metadata.mapNotNull { entry ->
                extractTmdbIdFromMetadata(entry)
            }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch watchlist", e)
            emptySet()
        }
    }

    suspend fun isOnWatchlist(tmdbId: Int, isMovie: Boolean, watchlistCache: Set<Int>? = null): Boolean {
        val ids = watchlistCache ?: fetchWatchlistTmdbIds()
        return tmdbId in ids
    }

    suspend fun addToWatchlist(item: TmdbMediaItem): PlexWatchlistResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext PlexWatchlistResult(
                success = false,
                message = "Connect Plex in Settings to request content",
            )
        }

        val ratingKey = resolveRatingKey(item)
            ?: return@withContext PlexWatchlistResult(
                success = false,
                message = "Could not resolve Plex metadata for ${item.displayTitle}",
            )

        if (isOnWatchlist(item.id, item.isMovie)) {
            return@withContext PlexWatchlistResult(
                success = true,
                message = "Already on your Plex watchlist",
                alreadyOnWatchlist = true,
            )
        }

        try {
            val url = "$DISCOVER_BASE/actions/addToWatchlist?ratingKey=$ratingKey&X-Plex-Token=$token"
            val request = Request.Builder()
                .url(url)
                .put("".toRequestBody("application/json".toMediaType()))
                .header("X-Plex-Token", token)
                .header("X-Plex-Client-Identifier", CLIENT_ID)
                .header("X-Plex-Product", "Rushy")
                .header("X-Plex-Version", "1.2.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            when {
                response.isSuccessful -> PlexWatchlistResult(
                    success = true,
                    message = "Added to Plex watchlist — your server will download soon",
                )
                response.code == 409 || response.code == 400 -> {
                    val body = response.body?.string().orEmpty()
                    if (body.contains("already", ignoreCase = true)) {
                        PlexWatchlistResult(true, "Already on your Plex watchlist", alreadyOnWatchlist = true)
                    } else {
                        PlexWatchlistResult(false, "Plex rejected request (${response.code})")
                    }
                }
                else -> PlexWatchlistResult(false, "Plex watchlist error (${response.code})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "addToWatchlist failed", e)
            PlexWatchlistResult(false, e.message ?: "Failed to add to watchlist")
        }
    }

    suspend fun searchServerLibrary(title: String, isMovie: Boolean): MediaItem? = withContext(Dispatchers.IO) {
        if (serverUrl.isNullOrBlank() || token.isBlank()) return@withContext null
        try {
            val base = PlexClient.normalizeServerUrl(serverUrl)
            val typeFilter = if (isMovie) "movie" else "show"
            val url = "${base}search?query=${java.net.URLEncoder.encode(title, "UTF-8")}&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("X-Plex-Token", token)
                .header("Accept", "application/json")
                .header("X-Plex-Client-Identifier", CLIENT_ID)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val parsed = gson.fromJson(body, PlexDiscoverContainer::class.java)
            @Suppress("UNCHECKED_CAST")
            val metadata = (parsed.mediaContainer as? Map<*, *>)?.get("Metadata") as? List<Map<*, *>>
                ?: return@withContext null
            val match = metadata.firstOrNull { meta ->
                val type = meta["type"] as? String
                type == typeFilter || type == null
            } ?: metadata.firstOrNull() ?: return@withContext null

            val ratingKey = match["ratingKey"] as? String ?: return@withContext null
            val matchTitle = match["title"] as? String ?: title
            MediaItem(
                id = "plex_$ratingKey",
                title = matchTitle,
                source = MediaSource.PLEX,
                playbackId = ratingKey,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Plex library search failed", e)
            null
        }
    }

    private fun resolveRatingKey(item: TmdbMediaItem): String? {
        val direct = if (item.isMovie) "tmdb-movie-${item.id}" else "tmdb-show-${item.id}"
        runCatching {
            val url = "$DISCOVER_BASE/library/metadata/$direct"
            val request = plexRequest(url)
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return direct
            }
        }
        return direct
    }

    private fun plexRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("X-Plex-Token", token)
            .header("X-Plex-Client-Identifier", CLIENT_ID)
            .header("X-Plex-Product", "Rushy")
            .header("X-Plex-Version", "1.2.0")
            .header("Accept", "application/json")
            .build()
    }

    private fun extractTmdbIdFromMetadata(entry: Map<*, *>): Int? {
        val guids = entry["Guid"] as? List<*> ?: return null
        for (guid in guids) {
            val map = guid as? Map<*, *> ?: continue
            val id = map["id"] as? String ?: continue
            if (id.startsWith("tmdb://")) {
                return id.removePrefix("tmdb://").toIntOrNull()
            }
        }
        val ratingKey = entry["ratingKey"] as? String ?: return null
        return when {
            ratingKey.startsWith("tmdb-movie-") -> ratingKey.removePrefix("tmdb-movie-").toIntOrNull()
            ratingKey.startsWith("tmdb-show-") -> ratingKey.removePrefix("tmdb-show-").toIntOrNull()
            else -> null
        }
    }

    companion object {
        private const val TAG = "PlexWatchlistClient"
        private const val DISCOVER_BASE = "https://discover.provider.plex.tv"
        private const val CLIENT_ID = "rushy-android-tv-1.2.0"

        fun fromCredentials(credentials: CredentialStore): PlexWatchlistClient {
            return PlexWatchlistClient(
                token = credentials.plexToken,
                serverUrl = credentials.plexServerUrl.takeIf { it.isNotBlank() },
            )
        }
    }
}
