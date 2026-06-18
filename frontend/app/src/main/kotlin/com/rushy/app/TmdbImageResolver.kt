package com.rushy.app

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private data class TmdbDetail(
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
)

/** Lazy TMDB poster lookup when Trakt omits image URLs. */
object TmdbImageResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val posterCache = ConcurrentHashMap<String, String?>()

    fun posterUrlForId(id: String, isMovie: Boolean): String? {
        val key = "${if (isMovie) "m" else "t"}:$id"
        return posterCache.getOrPut(key) {
            fetchPath(id, isMovie)?.posterPath?.let { p ->
                if (p.startsWith("/")) "https://image.tmdb.org/t/p/w342$p" else p
            }
        }
    }

    fun backdropUrlForId(id: String, isMovie: Boolean): String? {
        val detail = fetchPath(id, isMovie) ?: return null
        return detail.backdropPath?.let { p ->
            if (p.startsWith("/")) "https://image.tmdb.org/t/p/w780$p" else p
        }
    }

    private fun fetchPath(id: String, isMovie: Boolean): TmdbDetail? {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return null
        val type = if (isMovie) "movie" else "tv"
        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/$type/$id?api_key=$apiKey")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                gson.fromJson(response.body?.string(), TmdbDetail::class.java)
            }
        }.getOrNull()
    }
}
