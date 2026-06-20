package com.streamvault.domain.model

/**
 * Whether a Plex-sourced search/catalog item can be played now vs needs to be requested.
 */
enum class PlexAvailability {
    /** File exists on the Plex server and playback URL is direct (not transcode-only). */
    PLAYABLE,

    /** Known Plex metadata match but no local media file yet. */
    REQUESTABLE,

    /** Not a Plex fallback item, or availability has not been resolved. */
    UNKNOWN
}

private val PlexTranscodeFallbackMarker = "transcode/universal/start.m3u8"

fun Movie.resolvePlexAvailability(): PlexAvailability {
    if (!showsPlexBadge()) return PlexAvailability.UNKNOWN
    plexAvailability?.let { return it }
    return if (hasDirectPlexMediaStream(streamUrl)) PlexAvailability.PLAYABLE else PlexAvailability.REQUESTABLE
}

fun Series.resolvePlexAvailability(): PlexAvailability {
    if (!showsPlexBadge()) return PlexAvailability.UNKNOWN
    plexAvailability?.let { return it }
    return if (plexHasPlayableEpisodes == true) {
        PlexAvailability.PLAYABLE
    } else if (plexHasPlayableEpisodes == false) {
        PlexAvailability.REQUESTABLE
    } else {
        PlexAvailability.UNKNOWN
    }
}

fun hasDirectPlexMediaStream(streamUrl: String): Boolean =
    streamUrl.isNotBlank() && !streamUrl.contains(PlexTranscodeFallbackMarker, ignoreCase = true)
