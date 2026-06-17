package com.rushy.app

data class MediaItem(
    val id: String,
    val title: String,
    val source: MediaSource,
    val playbackId: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val plot: String? = null,
    val rating: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
)

enum class MediaSource {
    XTREAM_LIVE,
    XTREAM_VOD,
    XTREAM_SERIES,
    PLEX,
    DEMO,
}

data class CatalogSummary(
    val liveCount: Int = 0,
    val vodCount: Int = 0,
    val seriesCount: Int = 0,
    val plexCount: Int = 0,
    val favoriteCount: Int = 0,
    val liveCategories: List<ChannelCategory> = emptyList(),
    val vodCategories: List<ChannelCategory> = emptyList(),
) {
    val movieCount: Int get() = vodCount + seriesCount
}

/** @deprecated Use [CatalogSummary] — avoids holding full catalogs in Compose state. */
data class DashboardData(
    val favorites: List<MediaItem> = emptyList(),
    val liveTv: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val plexLibrary: List<MediaItem> = emptyList(),
    val categories: List<ChannelCategory> = emptyList(),
    val categoryGroups: List<CategoryGroup> = emptyList(),
)

data class SearchResult(
    val exactMatches: List<MediaItem> = emptyList(),
    val nearMatches: List<MediaItem> = emptyList(),
)
