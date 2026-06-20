package com.streamvault.data.remote.plex

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.streamvault.data.remote.arr.WatchlistRequestResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class PlexDiscoverHit(
    val ratingKey: String,
    val title: String,
    val type: String,
    val tmdbId: Long?,
    val year: String?,
    val posterUrl: String?,
    val summary: String?,
)

@Singleton
class PlexDiscoverClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val plexCredentials: PlexCredentialStore,
) {
    private val client = okHttpClient.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun searchDiscover(query: String, includeMovies: Boolean, includeSeries: Boolean): List<PlexDiscoverHit> {
        if (plexCredentials.token.isBlank() || query.length < 2) return emptyList()
        val types = buildList {
            if (includeMovies) add("movie")
            if (includeSeries) add("show")
        }
        if (types.isEmpty()) return emptyList()

        return runCatching {
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
            val typeParam = types.joinToString(",")
            val url = "$DISCOVER_BASE/search?query=$encodedQuery&limit=12&searchTypes=$typeParam"
            val request = plexRequest(url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson<PlexDiscoverSearchResponse>(
                    body,
                    object : TypeToken<PlexDiscoverSearchResponse>() {}.type,
                )
                parsed.mediaContainer?.searchResults.orEmpty().mapNotNull { result ->
                    val metadata = result.metadata ?: return@mapNotNull null
                    val ratingKey = metadata.ratingKey ?: return@mapNotNull null
                    val title = metadata.title ?: return@mapNotNull null
                    val type = metadata.type ?: return@mapNotNull null
                    PlexDiscoverHit(
                        ratingKey = ratingKey,
                        title = title,
                        type = type,
                        tmdbId = metadata.tmdbId(),
                        year = metadata.year?.toString(),
                        posterUrl = metadata.thumb?.let { thumb ->
                            if (thumb.startsWith("http")) thumb else "$DISCOVER_BASE$thumb"
                        },
                        summary = metadata.summary,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addToWatchlist(ratingKey: String, title: String): WatchlistRequestResult {
        val token = plexCredentials.token
        if (token.isBlank()) {
            return WatchlistRequestResult(
                success = false,
                message = "Connect Plex in Settings to request content",
            )
        }
        return runCatching {
            val url = "$DISCOVER_BASE/actions/addToWatchlist?ratingKey=${enc(ratingKey)}&X-Plex-Token=${enc(token)}"
            val request = Request.Builder()
                .url(url)
                .put("".toRequestBody("application/json".toMediaType()))
                .header("X-Plex-Token", token)
                .header("X-Plex-Client-Identifier", CLIENT_ID)
                .header("X-Plex-Product", "Rushy")
                .header("X-Plex-Version", "2.4.6")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> WatchlistRequestResult(
                        success = true,
                        message = "Added $title to Plex watchlist — your server will download soon",
                    )
                    response.code == 409 || response.code == 400 -> {
                        val body = response.body?.string().orEmpty()
                        if (body.contains("already", ignoreCase = true)) {
                            WatchlistRequestResult(true, "$title is already on your Plex watchlist", alreadyRequested = true)
                        } else {
                            WatchlistRequestResult(false, "Plex rejected watchlist request (${response.code})")
                        }
                    }
                    else -> WatchlistRequestResult(false, "Plex watchlist error (${response.code})")
                }
            }
        }.getOrElse { error ->
            WatchlistRequestResult(false, error.message ?: "Failed to add to Plex watchlist")
        }
    }

    fun resolveDiscoverRatingKey(tmdbId: Long, isMovie: Boolean): String? {
        if (plexCredentials.token.isBlank()) return null
        val direct = if (isMovie) "tmdb-movie-$tmdbId" else "tmdb-show-$tmdbId"
        return runCatching {
            val request = plexRequest("$DISCOVER_BASE/library/metadata/$direct")
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) direct else null
            }
        }.getOrNull()
    }

    private fun plexRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("X-Plex-Token", plexCredentials.token)
            .header("X-Plex-Client-Identifier", CLIENT_ID)
            .header("X-Plex-Product", "Rushy")
            .header("X-Plex-Version", "2.4.6")
            .header("Accept", "application/json")
            .get()
            .build()

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val DISCOVER_BASE = "https://discover.provider.plex.tv"
        const val CLIENT_ID = "rushy-android-tv-2.4.6"
    }
}

private data class PlexDiscoverSearchResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexDiscoverSearchContainer? = null,
)

private data class PlexDiscoverSearchContainer(
    @SerializedName("SearchResult") val searchResults: List<PlexDiscoverSearchResult>? = null,
)

private data class PlexDiscoverSearchResult(
    @SerializedName("Metadata") val metadata: PlexDiscoverMetadataDto? = null,
)

private data class PlexDiscoverMetadataDto(
    @SerializedName("ratingKey") val ratingKey: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("Guid") val guid: List<PlexDiscoverGuidDto>? = null,
) {
    fun tmdbId(): Long? = guid
        ?.mapNotNull { it.id }
        ?.firstOrNull { it.startsWith("tmdb://") || it.startsWith("com.plexapp.agents.themoviedb://") }
        ?.substringAfterLast('/')
        ?.toLongOrNull()
        ?: ratingKey?.let { key ->
            when {
                key.startsWith("tmdb-movie-") -> key.removePrefix("tmdb-movie-").toLongOrNull()
                key.startsWith("tmdb-show-") -> key.removePrefix("tmdb-show-").toLongOrNull()
                else -> null
            }
        }
}

private data class PlexDiscoverGuidDto(
    @SerializedName("id") val id: String? = null,
)
