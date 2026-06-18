package com.streamvault.domain.model

enum class CatalogSource {
    PLEX,
    XTREAM,
    OTHER
}

fun ProviderType.toCatalogSource(): CatalogSource = when (this) {
    ProviderType.PLEX -> CatalogSource.PLEX
    ProviderType.XTREAM_CODES,
    ProviderType.M3U,
    ProviderType.STALKER_PORTAL -> CatalogSource.XTREAM
    else -> CatalogSource.OTHER
}

fun Movie.showsPlexBadge(): Boolean =
    catalogSource == CatalogSource.PLEX ||
        categoryName?.startsWith("Plex", ignoreCase = true) == true

fun Series.showsPlexBadge(): Boolean =
    catalogSource == CatalogSource.PLEX ||
        categoryName?.startsWith("Plex", ignoreCase = true) == true

fun Channel.showsPlexBadge(): Boolean = catalogSource == CatalogSource.PLEX
