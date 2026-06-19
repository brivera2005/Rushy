package com.streamvault.app.player.external

import android.content.Context
import android.content.Intent

/**
 * Legacy alias for [ExternalPlayerRouter] live playback APIs.
 */
object LivePlaybackRouter {
    const val TIVIMATE_PACKAGE = ExternalPlayerRouter.TIVIMATE_PACKAGE
    const val VLC_PACKAGE = ExternalPlayerRouter.VLC_PACKAGE

    typealias ExternalPlayerTarget = ExternalPlayerRouter.ExternalPlayerTarget
    typealias LiveLaunchRequest = ExternalPlayerRouter.LiveLaunchRequest

    sealed interface LiveLaunchResult {
        data class Success(val target: ExternalPlayerTarget) : LiveLaunchResult
        data object AllFailed : LiveLaunchResult
        data object OpenedPlayStore : LiveLaunchResult
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    fun resolvePreferredExternalPlayer(context: Context): ExternalPlayerTarget =
        ExternalPlayerRouter.resolvePreferredExternalPlayer(context)

    fun preferredExternalPlayerLabel(context: Context): String =
        ExternalPlayerRouter.preferredExternalPlayerLabel(context)

    fun buildTiviMateDeepLinkIntent(streamId: Long): Intent =
        ExternalPlayerRouter.buildTiviMateDeepLinkIntent(streamId)

    fun buildPackageViewIntent(url: String, packageName: String?): Intent? =
        ExternalPlayerRouter.buildPackageViewIntent(url, packageName)

    fun toMpegTsUrl(url: String): String = ExternalPlayerRouter.toMpegTsUrl(url)

    fun launchLiveStream(context: Context, request: LiveLaunchRequest): LiveLaunchResult =
        when (val result = ExternalPlayerRouter.launchLiveStream(context, request)) {
            is ExternalPlayerRouter.PlayResult.Success ->
                LiveLaunchResult.Success(result.target)
            ExternalPlayerRouter.PlayResult.OpenedPlayStore ->
                LiveLaunchResult.OpenedPlayStore
            ExternalPlayerRouter.PlayResult.AllFailed ->
                LiveLaunchResult.AllFailed
        }
}
