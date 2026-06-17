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
) {
    fun play(item: MediaItem) {
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

    private fun launchBuiltin(item: MediaItem) {
        val streamUrl = resolveStreamUrl(item)
        if (streamUrl.isNullOrBlank()) {
            showError("No stream URL available for ${item.title}.")
            return
        }
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_TITLE, item.title)
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
            )
        }
    }
}
