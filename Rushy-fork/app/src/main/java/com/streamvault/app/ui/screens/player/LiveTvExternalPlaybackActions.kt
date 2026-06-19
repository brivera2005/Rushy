package com.streamvault.app.ui.screens.player

import com.streamvault.app.player.external.ExternalPlayerLauncher
import com.streamvault.app.player.external.ExternalPlayerRouter
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.LiveTvPlayerMode

internal fun PlayerViewModel.shouldDelegateLivePlayback(mode: LiveTvPlayerMode): Boolean =
    currentContentType == ContentType.LIVE &&
        !isCatchUpPlayback() &&
        mode != LiveTvPlayerMode.INTERNAL &&
        mode != LiveTvPlayerMode.TIVIMATE_ON_STALL

internal suspend fun PlayerViewModel.tryDelegateLivePlaybackAtStart(
    mode: LiveTvPlayerMode,
    playbackUrl: String
): Boolean {
    if (!shouldDelegateLivePlayback(mode)) return false
    return when (mode) {
        LiveTvPlayerMode.TIVIMATE -> launchExternalLivePlayback(finishPlayer = true)
        LiveTvPlayerMode.EXTERNAL -> launchExternalPlayerForCurrentStream(finishPlayer = true)
        LiveTvPlayerMode.INTERNAL,
        LiveTvPlayerMode.TIVIMATE_ON_STALL -> false
    }
}

internal fun PlayerViewModel.launchExternalLivePlayback(finishPlayer: Boolean = false): Boolean {
    val url = currentResolvedPlaybackUrl.takeIf { it.isNotBlank() }
        ?: currentStreamUrl.takeIf { it.isNotBlank() }
        ?: return false
    val streamId = currentChannelFlow.value?.streamId?.takeIf { it > 0L }
        ?: currentChannelFlow.value?.selectedVariant?.streamId?.takeIf { it > 0L }
        ?: 0L
    return when (
        val result = ExternalPlayerRouter.playLiveChannel(
            context = appContext,
            url = url,
            title = currentTitle,
            streamId = streamId,
            preferMpegTs = true
        )
    ) {
        is ExternalPlayerRouter.PlayResult.Success -> {
            if (finishPlayer) {
                requestPlayerExit("Opened ${currentTitle.ifBlank { "channel" }} in ${result.target.displayName}.")
            } else {
                showPlayerNotice(
                    message = "Opened in ${result.target.displayName}. Returning to the previous screen.",
                    durationMs = 4_000L
                )
                requestPlayerExit()
            }
            true
        }
        ExternalPlayerRouter.PlayResult.OpenedPlayStore -> {
            showPlayerNotice(
                message = "No external player found. Opened Play Store to install VLC.",
                durationMs = 6_000L
            )
            if (finishPlayer) requestPlayerExit()
            true
        }
        ExternalPlayerRouter.PlayResult.AllFailed -> {
            showPlayerNotice(
                message = "Could not open an external player for this channel.",
                recoveryType = PlayerRecoveryType.SOURCE,
                actions = buildLiveExternalRecoveryActions()
            )
            false
        }
    }
}

internal fun PlayerViewModel.launchExternalPlayerForCurrentStream(finishPlayer: Boolean = false): Boolean {
    val url = currentResolvedPlaybackUrl.takeIf { it.isNotBlank() }
        ?: currentStreamUrl.takeIf { it.isNotBlank() }
        ?: return false
    return when (val result = ExternalPlayerLauncher.launch(appContext, url)) {
        is com.streamvault.app.player.external.ExternalPlayerLaunchResult.Success -> {
            if (finishPlayer) {
                requestPlayerExit("Opened stream in external player.")
            } else {
                showPlayerNotice(message = "Opened in external player.", durationMs = 4_000L)
                requestPlayerExit()
            }
            true
        }
        is com.streamvault.app.player.external.ExternalPlayerLaunchResult.NoHandler -> {
            launchExternalLivePlayback(finishPlayer = finishPlayer)
        }
        is com.streamvault.app.player.external.ExternalPlayerLaunchResult.InvalidUrl -> {
            showPlayerNotice(
                message = result.reason,
                recoveryType = PlayerRecoveryType.SOURCE
            )
            false
        }
        is com.streamvault.app.player.external.ExternalPlayerLaunchResult.Failed -> {
            showPlayerNotice(
                message = result.errorMessage,
                recoveryType = PlayerRecoveryType.SOURCE,
                actions = buildLiveExternalRecoveryActions()
            )
            false
        }
    }
}

internal fun PlayerViewModel.buildLiveExternalRecoveryActions(
    includeTiviMate: Boolean = ExternalPlayerRouter.isTiviMateInstalled(appContext),
    includeExternal: Boolean = true
): List<PlayerNoticeAction> {
    val actions = mutableListOf<PlayerNoticeAction>()
    if (includeTiviMate) actions += PlayerNoticeAction.OPEN_TIVIMATE
    if (includeExternal) actions += PlayerNoticeAction.OPEN_EXTERNAL_PLAYER
    actions += PlayerNoticeAction.RETRY
    if (hasLastChannel()) actions += PlayerNoticeAction.LAST_CHANNEL
    return actions.distinct()
}

internal fun PlayerViewModel.requestPlayerExit(noticeMessage: String? = null) {
    noticeMessage?.let { showPlayerNotice(message = it, durationMs = 2_500L) }
    _playerExitRequested.tryEmit(Unit)
}
