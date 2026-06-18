package com.rushy.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

class PlaybackRouter(
    private val context: Context,
    private val credentials: CredentialStore,
    private val playerSettings: PlayerSettings,
    private val recentChannels: RecentChannelsStore,
) {
    fun play(item: MediaItem) {
        if (item.source == MediaSource.XTREAM_LIVE || item.source == MediaSource.DEMO) {
            recentChannels.record(item.id)
        }
        val contentType = playerSettings.contentTypeFor(item)
        val player = playerSettings.getPlayer(contentType)
        when (player) {
            PlayerType.BUILTIN -> launchBuiltin(item)
            PlayerType.TIVIMATE -> launchTiviMate(item)
            PlayerType.PLEX -> launchPlex(item)
            PlayerType.VLC -> launchExternalPlayer(item, "org.videolan.vlc")
            PlayerType.MPV -> launchExternalPlayer(item, "is.xyz.mpv")
            PlayerType.EXTERNAL -> launchExternalPlayer(item, packageName = null)
        }
    }

    fun playCatchup(item: MediaItem, program: EpgProgram) {
        if (!item.tvArchive) {
            showError("Catch-up is not available for ${item.title}.")
            return
        }
        val now = System.currentTimeMillis() / 1000
        if (program.endEpochSec > now) {
            play(item)
            return
        }
        if (item.tvArchiveDurationHours != null) {
            val archiveStart = now - item.tvArchiveDurationHours * 3600L
            if (program.startEpochSec < archiveStart) {
                showError("This program is outside the ${item.tvArchiveDurationHours}h archive window.")
                return
            }
        }
        recentChannels.record(item.id)
        val streamUrl = resolveCatchupUrl(item, program)
        if (streamUrl.isNullOrBlank()) {
            showError("Could not build catch-up URL for ${program.title}.")
            return
        }
        val contentType = playerSettings.contentTypeFor(item)
        val player = playerSettings.getPlayer(contentType)
        when (player) {
            PlayerType.BUILTIN, PlayerType.VLC, PlayerType.MPV, PlayerType.EXTERNAL ->
                launchBuiltin(item, streamUrlOverride = streamUrl, isLive = false, title = program.title)
            PlayerType.TIVIMATE, PlayerType.PLEX ->
                launchBuiltin(item, streamUrlOverride = streamUrl, isLive = false, title = program.title)
        }
    }

    private fun launchBuiltin(
        item: MediaItem,
        streamUrlOverride: String? = null,
        isLive: Boolean? = null,
        title: String? = null,
    ) {
        val streamUrl = streamUrlOverride ?: resolveStreamUrl(item)
        if (streamUrl.isNullOrBlank()) {
            showError("No stream URL available for ${item.title}.")
            return
        }
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_TITLE, title ?: item.title)
            putExtra(PlayerActivity.EXTRA_IS_LIVE, isLive ?: (item.source == MediaSource.XTREAM_LIVE))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun launchTiviMate(item: MediaItem) {
        if (!isPackageInstalled("ar.tvplayer.tv")) {
            fallbackOrError(item, "TiviMate is not installed.")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("ar.tvplayer.tv")
                data = Uri.parse("tivimate://watch?id=${item.playbackId}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            fallbackOrError(item, "Could not open TiviMate.")
        }
    }

    private fun launchPlex(item: MediaItem) {
        if (!isPackageInstalled("com.plexapp.android")) {
            showError("Plex app is not installed.")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.plexapp.android")
                data = Uri.parse("plex://server/play?key=${item.playbackId}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            showError("Could not open Plex.")
        }
    }

    private fun launchExternalPlayer(item: MediaItem, packageName: String?) {
        val streamUrl = resolveStreamUrl(item)
        if (streamUrl.isNullOrBlank()) {
            showError("No stream URL available for ${item.title}.")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (packageName != null) setPackage(packageName)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            if (packageName != null) {
                launchExternalPlayer(item, packageName = null)
            } else {
                fallbackOrError(item, "No external player could open this stream.")
            }
        }
    }

    private fun resolveStreamUrl(item: MediaItem): String? {
        if (item.source == MediaSource.PLEX) return null
        if (!credentials.hasXtreamCredentials() && item.source != MediaSource.DEMO) return null
        if (item.source == MediaSource.DEMO) {
            return "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        }
        return StreamUrlBuilder.buildStreamUrl(
            credentials.xtreamPortal,
            credentials.xtreamUsername,
            credentials.xtreamPassword,
            item,
        )
    }

    private fun resolveCatchupUrl(item: MediaItem, program: EpgProgram): String? {
        if (!credentials.hasXtreamCredentials()) return null
        return StreamUrlBuilder.buildCatchupUrl(
            credentials.xtreamPortal,
            credentials.xtreamUsername,
            credentials.xtreamPassword,
            item,
            program,
        )
    }

    private fun fallbackOrError(item: MediaItem, message: String) {
        Toast.makeText(context, "$message Trying built-in player.", Toast.LENGTH_SHORT).show()
        launchBuiltin(item)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun getInstance(context: Context): PlaybackRouter {
            return PlaybackRouter(
                context.applicationContext,
                CredentialStore.getInstance(context),
                PlayerSettings.getInstance(context),
                RecentChannelsStore.getInstance(context),
            )
        }
    }
}
