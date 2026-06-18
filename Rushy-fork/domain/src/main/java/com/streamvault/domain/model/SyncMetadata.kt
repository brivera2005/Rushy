package com.streamvault.domain.model

enum class VodSyncMode {
    UNKNOWN,
    FAST_SYNC,
    FULL,
    CATEGORY_BULK,
    PAGED,
    LAZY_BY_CATEGORY
}

data class SyncMetadata(
    val providerId: Long,
    val lastLiveSync: Long = 0,
    val lastLiveSuccess: Long = 0,
    val lastMovieSync: Long = 0,
    val lastSeriesSync: Long = 0,
    val lastSeriesSuccess: Long = 0,
    val lastEpgSync: Long = 0,
    val lastEpgSuccess: Long = 0,
    val lastMovieAttempt: Long = 0,
    val lastMovieSuccess: Long = 0,
    val lastMoviePartial: Long = 0,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val epgCount: Int = 0,
    val lastSyncStatus: String = "NONE",
    val movieSyncMode: VodSyncMode = VodSyncMode.UNKNOWN,
    val movieWarningsCount: Int = 0,
    val movieCatalogStale: Boolean = false,
    val liveAvoidFullUntil: Long = 0,
    val movieAvoidFullUntil: Long = 0,
    val seriesAvoidFullUntil: Long = 0,
    val liveSequentialFailuresRemembered: Boolean = false,
    val liveHealthySyncStreak: Int = 0,
    val movieParallelFailuresRemembered: Boolean = false,
    val movieHealthySyncStreak: Int = 0,
    val seriesSequentialFailuresRemembered: Boolean = false,
    val seriesHealthySyncStreak: Int = 0
) {
    init {
        require(liveCount >= 0) { "liveCount must be non-negative" }
        require(movieCount >= 0) { "movieCount must be non-negative" }
        require(seriesCount >= 0) { "seriesCount must be non-negative" }
        require(epgCount >= 0) { "epgCount must be non-negative" }
        require(movieWarningsCount >= 0) { "movieWarningsCount must be non-negative" }
        require(liveAvoidFullUntil >= 0) { "liveAvoidFullUntil must be non-negative" }
        require(movieAvoidFullUntil >= 0) { "movieAvoidFullUntil must be non-negative" }
        require(seriesAvoidFullUntil >= 0) { "seriesAvoidFullUntil must be non-negative" }
        require(liveHealthySyncStreak >= 0) { "liveHealthySyncStreak must be non-negative" }
        require(movieHealthySyncStreak >= 0) { "movieHealthySyncStreak must be non-negative" }
        require(seriesHealthySyncStreak >= 0) { "seriesHealthySyncStreak must be non-negative" }
    }
}
