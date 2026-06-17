package com.rushy.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

class EpgRepository private constructor(
    private val context: Context,
    private val credentials: CredentialStore,
) {
    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "epg_cache.json")
    private var cachedPrograms: Map<String, List<EpgProgram>> = emptyMap()
    private var cacheTimestamp: Long = 0L

    init {
        loadCache()
    }

    fun getProgramsForChannel(channelId: String): List<EpgProgram> =
        cachedPrograms[channelId].orEmpty()

    suspend fun getOrFetchEpg(channel: MediaItem): List<EpgProgram> = withContext(Dispatchers.IO) {
        cachedPrograms[channel.playbackId]?.let { return@withContext it }
        if (credentials.isDemoMode || !credentials.hasXtreamCredentials()) {
            return@withContext demoEpgForChannel(channel)
        }
        val client = XtreamClient(
            credentials.xtreamPortal,
            credentials.xtreamUsername,
            credentials.xtreamPassword,
        )
        val programs = client.fetchEpgForChannel(channel)
        if (programs.isNotEmpty()) {
            cachedPrograms = cachedPrograms + (channel.playbackId to programs)
            saveCache()
        }
        programs
    }

    suspend fun refreshEpg(channels: List<MediaItem>): Map<String, List<EpgProgram>> =
        withContext(Dispatchers.IO) {
            if (credentials.isDemoMode || !credentials.hasXtreamCredentials()) {
                return@withContext demoEpg(channels)
            }

            val result = mutableMapOf<String, List<EpgProgram>>()
            channels.take(40).forEach { channel ->
                result[channel.playbackId] = getOrFetchEpg(channel)
            }
            result
        }

    private fun demoEpgForChannel(channel: MediaItem): List<EpgProgram> {
        val now = System.currentTimeMillis() / 1000
        return (0 until 3).map { index ->
            val start = now + index * 3600L
            EpgProgram(
                id = "${channel.id}_$index",
                channelId = channel.playbackId,
                title = "Program ${index + 1}",
                description = "Demo guide for ${channel.title}",
                startEpochSec = start,
                endEpochSec = start + 3600,
            )
        }
    }

    private fun demoEpg(channels: List<MediaItem>): Map<String, List<EpgProgram>> {
        val now = System.currentTimeMillis() / 1000
        return channels.associate { channel ->
            val programs = (0 until 4).map { index ->
                val start = now + index * 3600L
                EpgProgram(
                    id = "${channel.id}_$index",
                    channelId = channel.playbackId,
                    title = "Program ${index + 1}",
                    description = "Demo guide entry for ${channel.title}",
                    startEpochSec = start,
                    endEpochSec = start + 3600,
                )
            }
            channel.playbackId to programs
        }
    }

    private fun loadCache() {
        if (!cacheFile.exists()) return
        runCatching {
            val wrapper = gson.fromJson(cacheFile.readText(), EpgCacheWrapper::class.java)
            cachedPrograms = wrapper.programs
            cacheTimestamp = wrapper.timestamp
        }
    }

    private fun saveCache() {
        cacheFile.writeText(
            gson.toJson(EpgCacheWrapper(cachedPrograms, cacheTimestamp)),
        )
    }

    private data class EpgCacheWrapper(
        val programs: Map<String, List<EpgProgram>>,
        val timestamp: Long,
    )

    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L

        @Volatile
        private var instance: EpgRepository? = null

        fun getInstance(context: Context): EpgRepository {
            return instance ?: synchronized(this) {
                instance ?: EpgRepository(context.applicationContext, CredentialStore.getInstance(context))
                    .also { instance = it }
            }
        }
    }
}
