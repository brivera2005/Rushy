package com.rushy.app

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class TrendingRepository private constructor(private val context: Context) {
    private val gson = Gson()
    private val cacheFile = File(context.cacheDir, "trakt_home_cache.json")
    private val traktSettings = TraktSettings.getInstance(context)
    private val traktClient = TraktApiClient(traktSettings)
    private val tmdbClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getHomeRows(forceRefresh: Boolean = false): TmdbHomeRows = withContext(Dispatchers.IO) {
        if (!traktSettings.hasClientId()) {
            return@withContext TmdbHomeRows(
                error = "Trakt client ID missing — rebuild with trakt.properties or check Settings → Trakt",
            )
        }

        if (!forceRefresh) {
            loadCache()?.let { cached ->
                if (System.currentTimeMillis() - cached.loadedAt < CACHE_TTL_MS) {
                    return@withContext cached
                }
            }
        }

        try {
            val rows = fetchFromTrakt()
            if (rows.hasContent) {
                cacheFile.writeText(gson.toJson(rows))
                return@withContext rows
            }
            val tmdbFallback = fetchFromTmdbFallback()
            if (tmdbFallback != null) {
                cacheFile.writeText(gson.toJson(tmdbFallback))
                return@withContext tmdbFallback
            }
            rows.copy(error = rows.error ?: "Trakt returned no trending data")
        } catch (e: Exception) {
            Log.e(TAG, "Trakt fetch failed", e)
            loadCache() ?: fetchFromTmdbFallback()
                ?: TmdbHomeRows(error = e.message ?: "Trakt unavailable")
        }
    }

    private suspend fun fetchFromTrakt(): TmdbHomeRows {
        val trendingMovies = traktClient.fetchTrendingMovies()
        val trendingTv = traktClient.fetchTrendingShows()
        val popularMovies = traktClient.fetchPopularMovies()
        val popularShows = traktClient.fetchPopularShows()
        val hero = (trendingMovies + trendingTv).randomOrNull()
        val error = if (
            trendingMovies.isEmpty() &&
            trendingTv.isEmpty() &&
            popularMovies.isEmpty() &&
            popularShows.isEmpty()
        ) {
            "Trakt returned empty rows — check TRAKT_CLIENT_ID"
        } else {
            null
        }
        return TmdbHomeRows(
            trendingMoviesDay = trendingMovies,
            trendingTvDay = trendingTv,
            popularMovies = popularMovies,
            topRatedMovies = popularShows,
            topRatedTv = emptyList(),
            heroItem = hero,
            loadedAt = System.currentTimeMillis(),
            error = error,
        )
    }

    private fun fetchFromTmdbFallback(): TmdbHomeRows? {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return null
        return try {
            val trendingMovies = fetchTmdbTrending("movie", "day", apiKey)
            val trendingTv = fetchTmdbTrending("tv", "day", apiKey)
            val popular = fetchTmdbList("/movie/popular", apiKey, "movie")
            val popularTv = fetchTmdbList("/tv/popular", apiKey, "tv")
            TmdbHomeRows(
                trendingMoviesDay = trendingMovies,
                trendingTvDay = trendingTv,
                popularMovies = popular,
                topRatedMovies = popularTv,
                topRatedTv = emptyList(),
                heroItem = (trendingMovies + trendingTv).randomOrNull(),
                loadedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fallback failed", e)
            null
        }
    }

    private fun fetchTmdbTrending(mediaType: String, window: String, apiKey: String): List<TmdbMediaItem> {
        val url = "$TMDB_BASE_URL/trending/$mediaType/$window?api_key=$apiKey"
        val response = tmdbClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return gson.fromJson(body, TmdbTrendingResponse::class.java).results.map {
            if (it.mediaType == null) it.copy(mediaType = mediaType) else it
        }
    }

    private fun fetchTmdbList(path: String, apiKey: String, mediaType: String): List<TmdbMediaItem> {
        val url = "$TMDB_BASE_URL$path?api_key=$apiKey"
        val response = tmdbClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return gson.fromJson(body, TmdbTrendingResponse::class.java).results.map {
            it.copy(mediaType = mediaType)
        }
    }

    private fun loadCache(): TmdbHomeRows? {
        if (!cacheFile.exists()) return null
        return runCatching { gson.fromJson(cacheFile.readText(), TmdbHomeRows::class.java) }.getOrNull()
    }

    /** Fuzzy-match trending title against local VOD catalog for playable items. */
    suspend fun matchToCatalog(
        tmdbItem: TmdbMediaItem,
        repository: LocalMediaRepository,
    ): MediaItem? = withContext(Dispatchers.IO) {
        val title = tmdbItem.displayTitle.lowercase()
        val candidates = repository.getItemsBySource(
            if (tmdbItem.isMovie) MediaSource.XTREAM_VOD else MediaSource.XTREAM_SERIES,
            limit = 500,
        )
        candidates.firstOrNull { item ->
            val itemTitle = item.title.lowercase()
            itemTitle == title ||
                itemTitle.contains(title) ||
                title.contains(itemTitle)
        }
    }

    companion object {
        private const val TAG = "TrendingRepository"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        @Volatile
        private var instance: TrendingRepository? = null

        fun getInstance(context: Context): TrendingRepository {
            return instance ?: synchronized(this) {
                instance ?: TrendingRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

private val TmdbHomeRows.hasContent: Boolean
    get() = trendingMoviesDay.isNotEmpty() ||
        trendingTvDay.isNotEmpty() ||
        popularMovies.isNotEmpty() ||
        topRatedMovies.isNotEmpty()
