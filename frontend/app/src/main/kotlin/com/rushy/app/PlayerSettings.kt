package com.rushy.app

import android.content.Context
import android.content.SharedPreferences

enum class PlayerType(val label: String, val packageName: String?) {
    BUILTIN("Built-in Player", null),
    TIVIMATE("TiviMate", "ar.tvplayer.tv"),
    VLC("VLC", "org.videolan.vlc"),
    MPV("MPV", "is.xyz.mpv"),
    PLEX("Plex", "com.plexapp.android"),
    EXTERNAL("System Picker", null),
}

enum class ContentType(val prefKey: String, val label: String) {
    LIVE_TV("player_live", "Live TV"),
    MOVIES("player_movies", "Movies"),
    SERIES("player_series", "TV Series"),
    PLEX("player_plex", "Plex"),
}

class PlayerSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPlayer(contentType: ContentType): PlayerType {
        val stored = prefs.getString(contentType.prefKey, null)
        return stored?.let { runCatching { PlayerType.valueOf(it) }.getOrNull() }
            ?: defaultFor(contentType)
    }

    fun setPlayer(contentType: ContentType, player: PlayerType) {
        prefs.edit().putString(contentType.prefKey, player.name).apply()
    }

    fun contentTypeFor(item: MediaItem): ContentType = when (item.source) {
        MediaSource.XTREAM_LIVE, MediaSource.DEMO -> ContentType.LIVE_TV
        MediaSource.XTREAM_VOD -> ContentType.MOVIES
        MediaSource.XTREAM_SERIES -> ContentType.SERIES
        MediaSource.PLEX -> ContentType.PLEX
    }

    companion object {
        private const val PREFS_NAME = "rushy_player_settings"

        fun defaultFor(contentType: ContentType): PlayerType = when (contentType) {
            ContentType.LIVE_TV, ContentType.MOVIES, ContentType.SERIES -> PlayerType.BUILTIN
            ContentType.PLEX -> PlayerType.PLEX
        }

        @Volatile
        private var instance: PlayerSettings? = null

        fun getInstance(context: Context): PlayerSettings {
            return instance ?: synchronized(this) {
                instance ?: PlayerSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
