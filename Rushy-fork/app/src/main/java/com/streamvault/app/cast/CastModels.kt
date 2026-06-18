package com.streamvault.app.cast

/**
 * Media payload sent to a Chromecast receiver.
 *
 * **Live channel URL strategy:** For Xtream live channels the [url] must be
 * the credential-based portal URL (no expiry tokens) so that long-running Cast
 * sessions are not interrupted when a tokenized CDN URL expires.
 * [PlayerViewModelActions.buildCastRequest] passes `preferStableUrl = true`
 * when resolving the live stream URL.
 */
data class CastMediaRequest(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val mimeType: String? = null,
    val isLive: Boolean = false,
    val startPositionMs: Long = 0L
)

enum class CastConnectionState {
    UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class CastStartResult {
    STARTED,
    ROUTE_SELECTION_REQUIRED,
    UNAVAILABLE,
    UNSUPPORTED
}