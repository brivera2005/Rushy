package com.rushy.app

object StreamUrlBuilder {

    fun buildStreamUrl(
        portal: String,
        username: String,
        password: String,
        item: MediaItem,
    ): String {
        val base = XtreamClient.normalizePortalUrl(portal).removeSuffix("/")
        val encodedUser = UriEncoder.encode(username)
        val encodedPass = UriEncoder.encode(password)
        val streamId = item.playbackId

        return when (item.source) {
            MediaSource.XTREAM_LIVE, MediaSource.DEMO ->
                "$base/live/$encodedUser/$encodedPass/$streamId.ts"
            MediaSource.XTREAM_VOD ->
                "$base/movie/$encodedUser/$encodedPass/$streamId.mkv"
            MediaSource.XTREAM_SERIES ->
                "$base/series/$encodedUser/$encodedPass/$streamId.mkv"
            MediaSource.PLEX -> ""
        }
    }
}

private object UriEncoder {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")
}
