package com.streamvault.app.player.external

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.streamvault.domain.model.ProviderType

/**
 * Zero-config external player routing for live TV and selected VOD streams.
 *
 * Priority chain for live channels:
 * 1. TiviMate (deep link, then direct URL)
 * 2. VLC (phone or Android TV package)
 * 3. Play Store VLC install page (one tap to install)
 */
object ExternalPlayerRouter {
    private const val TAG = "ExternalPlayerRouter"
    const val TIVIMATE_PACKAGE = "ar.tvplayer.tv"
    const val VLC_PACKAGE = "org.videolan.vlc"
    private const val VLC_PLAY_STORE_ID = VLC_PACKAGE
    private const val HLS_EXTENSION = ".m3u8"
    private const val MPEG_TS_EXTENSION = ".ts"

    val VLC_PACKAGE_CANDIDATES = listOf(
        VLC_PACKAGE,
        "org.videolan.vlc.tv",
    )

    enum class ExternalPlayerTarget(val packageName: String?, val displayName: String) {
        TIVIMATE(TIVIMATE_PACKAGE, "TiviMate"),
        VLC(VLC_PACKAGE, "VLC"),
        PLAY_STORE(null, "Play Store"),
    }

    data class CachedAvailability(
        val tiviMateInstalled: Boolean,
        val vlcInstalled: Boolean,
        val vlcPackage: String?,
        val refreshedAtMs: Long,
    )

    data class LiveLaunchRequest(
        val streamUrl: String,
        val streamId: Long = 0L,
        val title: String = "",
        val preferMpegTs: Boolean = true,
    )

    sealed interface PlayResult {
        data class Success(val target: ExternalPlayerTarget, val title: String = "") : PlayResult
        data object OpenedPlayStore : PlayResult
        data object AllFailed : PlayResult
    }

    @Volatile
    private var cachedAvailability: CachedAvailability? = null

    fun refreshAvailability(context: Context): CachedAvailability {
        val appContext = context.applicationContext
        val tiviMateInstalled = isPackageInstalled(appContext, TIVIMATE_PACKAGE)
        val vlcPackage = resolveInstalledVlcPackage(appContext)
        return CachedAvailability(
            tiviMateInstalled = tiviMateInstalled,
            vlcInstalled = vlcPackage != null,
            vlcPackage = vlcPackage,
            refreshedAtMs = System.currentTimeMillis(),
        ).also { cachedAvailability = it }
    }

    fun cachedAvailability(): CachedAvailability? = cachedAvailability

    fun isTiviMateInstalled(context: Context): Boolean =
        cachedAvailability?.tiviMateInstalled
            ?: isPackageInstalled(context.applicationContext, TIVIMATE_PACKAGE)

    fun isVlcInstalled(context: Context): Boolean =
        cachedAvailability?.vlcInstalled
            ?: resolveInstalledVlcPackage(context.applicationContext) != null

    fun resolvePreferredExternalPlayer(context: Context): ExternalPlayerTarget = when {
        isTiviMateInstalled(context) -> ExternalPlayerTarget.TIVIMATE
        isVlcInstalled(context) -> ExternalPlayerTarget.VLC
        else -> ExternalPlayerTarget.PLAY_STORE
    }

    fun preferredExternalPlayerLabel(context: Context): String =
        resolvePreferredExternalPlayer(context).displayName

    fun playLiveChannel(
        context: Context,
        url: String,
        title: String = "",
        streamId: Long = 0L,
        preferMpegTs: Boolean = true,
    ): PlayResult = launchLiveStream(
        context,
        LiveLaunchRequest(
            streamUrl = url,
            streamId = streamId,
            title = title,
            preferMpegTs = preferMpegTs,
        ),
    )

    fun launchLiveStream(context: Context, request: LiveLaunchRequest): PlayResult {
        val playbackUrl = when {
            request.preferMpegTs -> toMpegTsUrl(request.streamUrl)
            else -> request.streamUrl
        }.trim()

        if (isTiviMateInstalled(context)) {
            if (request.streamId > 0L) {
                if (tryStartActivity(context, buildTiviMateDeepLinkIntent(request.streamId))) {
                    logLaunch(ExternalPlayerTarget.TIVIMATE, request.title, request.streamId)
                    return PlayResult.Success(ExternalPlayerTarget.TIVIMATE, request.title)
                }
            }

            if (playbackUrl.isNotBlank()) {
                buildTiviMateDirectUrlIntent(playbackUrl)?.let { intent ->
                    if (tryStartActivity(context, intent)) {
                        logLaunch(ExternalPlayerTarget.TIVIMATE, request.title, request.streamId)
                        return PlayResult.Success(ExternalPlayerTarget.TIVIMATE, request.title)
                    }
                }

                Toast.makeText(
                    context.applicationContext,
                    "Opening TiviMate…",
                    Toast.LENGTH_SHORT,
                ).show()
                buildTiviMateDirectUrlIntent(playbackUrl, restrictPackage = false)?.let { intent ->
                    if (tryStartActivity(context, intent)) {
                        logLaunch(ExternalPlayerTarget.TIVIMATE, request.title, request.streamId)
                        return PlayResult.Success(ExternalPlayerTarget.TIVIMATE, request.title)
                    }
                }
            }
        }

        resolveInstalledVlcPackage(context)?.let { vlcPackage ->
            if (playbackUrl.isNotBlank()) {
                buildPackageViewIntent(playbackUrl, vlcPackage)?.let { intent ->
                    if (tryStartActivity(context, intent)) {
                        logLaunch(ExternalPlayerTarget.VLC, request.title, request.streamId)
                        return PlayResult.Success(ExternalPlayerTarget.VLC, request.title)
                    }
                }
            }
        }

        if (openVlcInstallPage(context)) {
            Log.i(TAG, "live-playback opened Play Store for VLC title=${request.title}")
            return PlayResult.OpenedPlayStore
        }

        Log.w(TAG, "live-playback all handlers failed title=${request.title}")
        return PlayResult.AllFailed
    }

    fun playVodStream(context: Context, url: String, title: String = ""): PlayResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return PlayResult.AllFailed

        resolveInstalledVlcPackage(context)?.let { vlcPackage ->
            buildPackageViewIntent(trimmed, vlcPackage)?.let { intent ->
                if (tryStartActivity(context, intent)) {
                    logLaunch(ExternalPlayerTarget.VLC, title)
                    return PlayResult.Success(ExternalPlayerTarget.VLC, title)
                }
            }
        }

        if (isTiviMateInstalled(context)) {
            buildPackageViewIntent(trimmed, TIVIMATE_PACKAGE)?.let { intent ->
                if (tryStartActivity(context, intent)) {
                    logLaunch(ExternalPlayerTarget.TIVIMATE, title)
                    return PlayResult.Success(ExternalPlayerTarget.TIVIMATE, title)
                }
            }
        }

        return PlayResult.AllFailed
    }

    fun shouldPlayVodExternally(providerType: ProviderType?, url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false
        return when (providerType) {
            ProviderType.PLEX -> true
            ProviderType.XTREAM_CODES,
            ProviderType.M3U,
            ProviderType.STALKER_PORTAL -> isLiveStyleUrl(trimmed)
            else -> false
        }
    }

    fun isLiveStyleUrl(url: String): Boolean {
        val path = url.trim().substringBefore("?").substringBefore("#").lowercase()
        return path.endsWith(HLS_EXTENSION) || path.endsWith(MPEG_TS_EXTENSION)
    }

    fun openVlcInstallPage(context: Context): Boolean {
        val appContext = context.applicationContext
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$VLC_PLAY_STORE_ID"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(marketIntent)
            true
        } catch (_: ActivityNotFoundException) {
            runCatching {
                appContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$VLC_PLAY_STORE_ID"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }

    fun buildTiviMateDeepLinkIntent(streamId: Long): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(TIVIMATE_PACKAGE)
            data = Uri.parse("tivimate://watch?id=$streamId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Legacy Rushy TiviMate handoff: ACTION_VIEW + optional package + stream URL (no MIME type). */
    fun buildTiviMateDirectUrlIntent(url: String, restrictPackage: Boolean = true): Intent? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        if (!ExternalPlayerLauncher.isExternalPlayerLaunchUrl(trimmed)) return null
        return Intent(Intent.ACTION_VIEW).apply {
            if (restrictPackage) setPackage(TIVIMATE_PACKAGE)
            data = Uri.parse(trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildPackageViewIntent(url: String, packageName: String?): Intent? {
        val launchUrl = url.trim().takeIf { it.isNotBlank() } ?: return null
        return ExternalPlayerLauncher.buildExternalPlayerIntent(launchUrl)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            packageName?.let(::setPackage)
        }
    }

    fun toMpegTsUrl(url: String): String {
        val queryStart = url.indexOf('?')
        val base = if (queryStart >= 0) url.substring(0, queryStart) else url
        val query = if (queryStart >= 0) url.substring(queryStart) else ""
        return if (base.endsWith(HLS_EXTENSION, ignoreCase = true)) {
            base.dropLast(HLS_EXTENSION.length) + MPEG_TS_EXTENSION + query
        } else {
            url
        }
    }

    private fun resolveInstalledVlcPackage(context: Context): String? {
        cachedAvailability?.vlcPackage?.let { return it }
        return VLC_PACKAGE_CANDIDATES.firstOrNull { isPackageInstalled(context, it) }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun tryStartActivity(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    private fun logLaunch(target: ExternalPlayerTarget, title: String, streamId: Long = 0L) {
        Log.i(
            TAG,
            "playback player=${target.displayName} package=${target.packageName ?: "n/a"} " +
                "title=$title streamId=$streamId",
        )
    }
}
