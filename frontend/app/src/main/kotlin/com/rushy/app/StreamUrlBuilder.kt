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

    fun buildCatchupUrl(
        portal: String,
        username: String,
        password: String,
        item: MediaItem,
        program: EpgProgram,
    ): String {
        val base = XtreamClient.normalizePortalUrl(portal).removeSuffix("/")
        val encodedUser = UriEncoder.encode(username)
        val encodedPass = UriEncoder.encode(password)
        val durationMinutes = ((program.endEpochSec - program.startEpochSec) / 60).coerceAtLeast(1)
        val start = CatchupTimeFormatter.format(program.startEpochSec)
        val encodedStart = UriEncoder.encode(start)
        return "$base/streaming/timeshift.php?username=$encodedUser&password=$encodedPass" +
            "&stream=${item.playbackId}&start=$encodedStart&duration=$durationMinutes"
    }
}

private object CatchupTimeFormatter {
    private val format = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)

    fun format(epochSec: Long): String = format.format(java.util.Date(epochSec * 1000))
}

private object UriEncoder {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")
}
