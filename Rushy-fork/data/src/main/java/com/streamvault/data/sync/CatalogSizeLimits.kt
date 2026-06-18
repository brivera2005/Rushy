package com.streamvault.data.sync

data class CatalogSizeLimits(
    val maxChannelsPerProvider: Int = 100_000,
    val maxMoviesPerProvider: Int = 200_000,
    val maxSeriesPerProvider: Int = 100_000
)
