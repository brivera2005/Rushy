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
    private val cacheFile = File(context.cacheDir, "tmdb_home_cache.json")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getHomeRows(forceRefresh: Boolean = false): TmdbHomeRows = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) {
            return@withContext TmdbHomeRows(error = "Add TMDB_API_KEY to local.properties")
        }

        if (!forceRefresh) {
            loadCache()?.let { cached ->
                if (System.currentTimeMillis() - cached.loadedAt < CACHE_TTL_MS) {
                    return@withContext cached
                }
            }
        }

        try {
            val trendingMovies = fetchTrending("movie", "day", apiKey)
            val trendingTv = fetchTrending("tv", "day", apiKey)
            val popular = fetchList("/movie/popular", apiKey, "movie")
            val topRated = fetchList("/movie/top_rated", apiKey, "movie")
            val topRatedTv = fetchList("/tv/top_rated", apiKey, "tv")
            val hero = (trendingMovies + trendingTv).randomOrNull()
            val rows = TmdbHomeRows(
                trendingMoviesDay = trendingMovies,
                trendingTvDay = trendingTv,
                popularMovies = popular,
                topRatedMovies = topRated,
                topRatedTv = topRatedTv,
                heroItem = hero,
                loadedAt = System.currentTimeMillis(),
            )
            cacheFile.writeText(gson.toJson(rows))
            rows
        } catch (e: Exception) {
            Log.e(TAG, "TMDB fetch failed", e)
            loadCache() ?: TmdbHomeRows(error = e.message ?: "TMDB unavailable")
        }
    }

    private fun fetchTrending(mediaType: String, window: String, apiKey: String): List<TmdbMediaItem> {
        val url = "$BASE_URL/trending/$mediaType/$window?api_key=$apiKey"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return gson.fromJson(body, TmdbTrendingResponse::class.java).results.map {
            if (it.mediaType == null) it.copy(mediaType = mediaType) else it
        }
    }

    private fun fetchList(path: String, apiKey: String, mediaType: String): List<TmdbMediaItem> {
        val url = "$BASE_URL$path?api_key=$apiKey"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
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

    /** Fuzzy-match TMDB title against local VOD catalog for playable items. */
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
        private const val BASE_URL = "https://api.themoviedb.org/3"
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
