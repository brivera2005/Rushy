package com.streamvault.data.remote.plex

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.streamvault.data.local.entity.EpisodeEntity
import com.streamvault.data.local.entity.MovieEntity
import com.streamvault.data.local.entity.SeriesEntity
import com.streamvault.domain.model.Provider
import com.streamvault.domain.model.Result
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private companion object {
        const val MOVIE_CATEGORY_ID = 1L
        const val SERIES_CATEGORY_ID = 2L
        const val REQUEST_TIMEOUT_SECONDS = 60L
        const val CLIENT_ID = "rushy-android-tv"
    }

    suspend fun validateCredentials(serverUrl: String, token: String): Result<Unit> = try {
        val session = session(serverUrl, token)
        val sections = session.fetchLibrarySections()
        if (sections.isEmpty()) {
            Result.error("Plex server returned no libraries")
        } else {
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.error("Plex authentication failed: ${e.message}", e)
    }

    suspend fun fetchMovies(provider: Provider): Result<List<MovieEntity>> = try {
        val token = provider.password
        val session = session(provider.serverUrl, token)
        val movies = mutableListOf<MovieEntity>()
        for (section in session.fetchLibrarySections()) {
            if (section.type != "movie") continue
            val sectionKey = section.key ?: continue
            val items = session.fetchSectionItems(sectionKey)
            items.forEach { item ->
                buildMovieEntity(item, provider, token, session.baseUrl)?.let(movies::add)
            }
        }
        Result.success(movies)
    } catch (e: Exception) {
        Result.error("Failed to load Plex movies: ${e.message}", e)
    }

    suspend fun fetchSeries(provider: Provider): Result<List<SeriesEntity>> = try {
        val token = provider.password
        val session = session(provider.serverUrl, token)
        val series = mutableListOf<SeriesEntity>()
        for (section in session.fetchLibrarySections()) {
            if (section.type != "show") continue
            val sectionKey = section.key ?: continue
            val items = session.fetchSectionItems(sectionKey)
            items.forEach { item ->
                buildSeriesEntity(item, provider, token, session.baseUrl)?.let(series::add)
            }
        }
        Result.success(series)
    } catch (e: Exception) {
        Result.error("Failed to load Plex series: ${e.message}", e)
    }

    suspend fun fetchEpisodes(
        provider: Provider,
        seriesRemoteId: String,
        seriesLocalId: Long,
    ): Result<List<EpisodeEntity>> = try {
        val token = provider.password
        val session = session(provider.serverUrl, token)
        val leaves = session.fetchAllLeaves(seriesRemoteId)
        val episodes = leaves.mapNotNull { item ->
            if (item.type != "episode") return@mapNotNull null
            buildEpisodeEntity(item, provider, token, session.baseUrl, seriesLocalId)
        }
        Result.success(episodes)
    } catch (e: Exception) {
        Result.error("Failed to load Plex episodes: ${e.message}", e)
    }

    private fun session(serverUrl: String, token: String): PlexSession {
        val baseUrl = PlexClient.normalizeServerUrl(serverUrl)
        return PlexSession(baseUrl, token.trim(), okHttpClient, gson)
    }

    private fun buildMovieEntity(
        item: PlexMetadataDto,
        provider: Provider,
        token: String,
        baseUrl: String,
    ): MovieEntity? {
        val ratingKey = item.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        val name = item.title?.takeIf { it.isNotBlank() } ?: return null
        return MovieEntity(
            streamId = stableRemoteId(ratingKey),
            name = name,
            posterUrl = buildImageUrl(baseUrl, item.thumb, token),
            backdropUrl = buildImageUrl(baseUrl, item.art, token),
            categoryId = MOVIE_CATEGORY_ID,
            categoryName = "Plex Movies",
            streamUrl = buildPlaybackUrl(baseUrl, item, ratingKey, token),
            containerExtension = item.primaryContainer(),
            plot = item.summary,
            genre = null,
            releaseDate = item.year?.toString(),
            duration = item.duration?.let(::formatDurationMs),
            durationSeconds = ((item.duration ?: 0L) / 1000L).toInt(),
            rating = item.rating ?: 0f,
            year = item.year?.toString(),
            tmdbId = item.tmdbId(),
            providerId = provider.id,
            isAdult = false,
            addedAt = (item.addedAt ?: 0L) * 1000L,
        )
    }

    private fun buildSeriesEntity(
        item: PlexMetadataDto,
        provider: Provider,
        token: String,
        baseUrl: String,
    ): SeriesEntity? {
        val ratingKey = item.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        val name = item.title?.takeIf { it.isNotBlank() } ?: return null
        return SeriesEntity(
            seriesId = stableRemoteId(ratingKey),
            providerSeriesId = ratingKey,
            name = name,
            posterUrl = buildImageUrl(baseUrl, item.thumb, token),
            backdropUrl = buildImageUrl(baseUrl, item.art, token),
            categoryId = SERIES_CATEGORY_ID,
            categoryName = "Plex Shows",
            plot = item.summary,
            genre = null,
            releaseDate = item.year?.toString(),
            rating = item.rating ?: 0f,
            tmdbId = item.tmdbId(),
            episodeRunTime = item.duration?.let(::formatDurationMs),
            lastModified = (item.addedAt ?: 0L) * 1000L,
            providerId = provider.id,
            isAdult = false,
        )
    }

    private fun buildEpisodeEntity(
        item: PlexMetadataDto,
        provider: Provider,
        token: String,
        baseUrl: String,
        seriesLocalId: Long,
    ): EpisodeEntity? {
        val ratingKey = item.ratingKey?.takeIf { it.isNotBlank() } ?: return null
        val title = item.title?.takeIf { it.isNotBlank() } ?: return null
        return EpisodeEntity(
            episodeId = stableRemoteId(ratingKey),
            title = title,
            episodeNumber = item.index ?: 0,
            seasonNumber = item.parentIndex ?: 0,
            streamUrl = buildPlaybackUrl(baseUrl, item, ratingKey, token),
            containerExtension = item.primaryContainer(),
            coverUrl = buildImageUrl(baseUrl, item.thumb, token),
            plot = item.summary,
            duration = item.duration?.let(::formatDurationMs),
            durationSeconds = ((item.duration ?: 0L) / 1000L).toInt(),
            rating = item.rating ?: 0f,
            releaseDate = item.year?.toString(),
            seriesId = seriesLocalId,
            providerId = provider.id,
            isAdult = false,
        )
    }

    private fun buildPlaybackUrl(
        baseUrl: String,
        item: PlexMetadataDto,
        ratingKey: String,
        token: String,
    ): String {
        val partKey = item.primaryPartKey()
        if (!partKey.isNullOrBlank()) {
            val path = if (partKey.startsWith("http")) partKey else "${baseUrl.trimEnd('/')}$partKey"
            return appendToken(path, token)
        }
        val encodedPath = URLEncoder.encode("/library/metadata/$ratingKey", Charsets.UTF_8.name())
        return "${baseUrl}video/:/transcode/universal/start.m3u8?path=$encodedPath&X-Plex-Token=${enc(token)}"
    }

    private fun buildImageUrl(baseUrl: String, path: String?, token: String): String? {
        if (path.isNullOrBlank()) return null
        val resolved = if (path.startsWith("http")) path else "${baseUrl.trimEnd('/')}$path"
        return appendToken(resolved, token)
    }

    private fun appendToken(url: String, token: String): String {
        val separator = if (url.contains('?')) "&" else "?"
        return "$url${separator}X-Plex-Token=${enc(token)}"
    }

    private fun stableRemoteId(value: String): Long {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        var result = 0L
        for (i in 0 until 8) result = (result shl 8) or (digest[i].toLong() and 0xff)
        return result and Long.MAX_VALUE
    }

    private fun formatDurationMs(durationMs: Long): String {
        val total = durationMs / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private class PlexSession(
        val baseUrl: String,
        private val token: String,
        okHttpClient: OkHttpClient,
        private val gson: Gson,
    ) {
        private val client = okHttpClient.newBuilder()
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        fun fetchLibrarySections(): List<PlexDirectoryDto> {
            val body = get("library/sections")
            val parsed = gson.fromJson<PlexSectionsResponseDto>(body, object : TypeToken<PlexSectionsResponseDto>() {}.type)
            return parsed.mediaContainer?.directories.orEmpty()
        }

        fun fetchSectionItems(sectionId: String): List<PlexMetadataDto> {
            val body = get("library/sections/$sectionId/all")
            val parsed = gson.fromJson<PlexMetadataResponseDto>(body, object : TypeToken<PlexMetadataResponseDto>() {}.type)
            return parsed.mediaContainer?.metadata.orEmpty()
        }

        fun fetchAllLeaves(showRatingKey: String): List<PlexMetadataDto> {
            val body = get("library/metadata/$showRatingKey/allLeaves")
            val parsed = gson.fromJson<PlexMetadataResponseDto>(body, object : TypeToken<PlexMetadataResponseDto>() {}.type)
            return parsed.mediaContainer?.metadata.orEmpty()
        }

        private fun get(path: String): String {
            val url = "${baseUrl}${path.trimStart('/')}"
            val request = Request.Builder()
                .url(url)
                .header("X-Plex-Token", token)
                .header("Accept", "application/json")
                .header("X-Plex-Client-Identifier", CLIENT_ID)
                .header("X-Plex-Product", "Rushy")
                .header("X-Plex-Version", "1.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Plex request failed with HTTP ${response.code}")
                }
                return response.body?.string().orEmpty()
            }
        }
    }
}

private data class PlexSectionsResponseDto(
    @SerializedName("MediaContainer") val mediaContainer: PlexSectionContainerDto? = null,
)

private data class PlexSectionContainerDto(
    @SerializedName("Directory") val directories: List<PlexDirectoryDto>? = null,
)

private data class PlexDirectoryDto(
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
)

private data class PlexMetadataResponseDto(
    @SerializedName("MediaContainer") val mediaContainer: PlexMetadataContainerDto? = null,
)

private data class PlexMetadataContainerDto(
    @SerializedName("Metadata") val metadata: List<PlexMetadataDto>? = null,
)

private data class PlexMetadataDto(
    @SerializedName("ratingKey") val ratingKey: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("art") val art: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("addedAt") val addedAt: Long? = null,
    @SerializedName("parentIndex") val parentIndex: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("Media") val media: List<PlexMediaDto>? = null,
    @SerializedName("Guid") val guid: List<PlexGuidDto>? = null,
) {
    fun primaryPartKey(): String? = media?.firstOrNull()?.part?.firstOrNull()?.key

    fun primaryContainer(): String? = media?.firstOrNull()?.container

    fun tmdbId(): Long? = guid
        ?.mapNotNull { it.id }
        ?.firstOrNull { it.startsWith("tmdb://") || it.startsWith("com.plexapp.agents.themoviedb://") }
        ?.substringAfterLast('/')
        ?.toLongOrNull()
}

private data class PlexMediaDto(
    @SerializedName("container") val container: String? = null,
    @SerializedName("Part") val part: List<PlexPartDto>? = null,
)

private data class PlexPartDto(
    @SerializedName("key") val key: String? = null,
)

private data class PlexGuidDto(
    @SerializedName("id") val id: String? = null,
)
