package com.rushy.app

import com.google.gson.annotations.SerializedName

data class TmdbTrendingResponse(
    val page: Int = 1,
    val results: List<TmdbMediaItem> = emptyList(),
)

data class TmdbMediaItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val posterUrl: String?
        get() = posterPath?.let { path ->
            if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w342$path"
        }
    val backdropUrl: String?
        get() = backdropPath?.let { path ->
            if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w1280$path"
        }
    val isMovie: Boolean get() = mediaType == "movie" || title != null
}

data class TmdbHomeRows(
    val trendingMoviesDay: List<TmdbMediaItem> = emptyList(),
    val trendingTvDay: List<TmdbMediaItem> = emptyList(),
    val popularMovies: List<TmdbMediaItem> = emptyList(),
    val topRatedMovies: List<TmdbMediaItem> = emptyList(),
    val topRatedTv: List<TmdbMediaItem> = emptyList(),
    val heroItem: TmdbMediaItem? = null,
    val error: String? = null,
    val loadedAt: Long = 0L,
)
