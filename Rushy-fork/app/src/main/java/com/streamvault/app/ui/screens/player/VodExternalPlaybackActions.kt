package com.streamvault.app.ui.screens.player

import com.streamvault.app.player.external.ExternalPlayerRouter
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ProviderType

internal suspend fun PlayerViewModel.tryDelegateVodPlaybackAtStart(
    providerType: ProviderType?,
    playbackUrl: String,
): Boolean {
    if (currentContentType == ContentType.LIVE) return false
    if (!ExternalPlayerRouter.shouldPlayVodExternally(providerType, playbackUrl)) return false
    if (!ExternalPlayerRouter.isVlcInstalled(appContext) &&
        !ExternalPlayerRouter.isTiviMateInstalled(appContext)
    ) {
        return false
    }
    return when (
        ExternalPlayerRouter.playVodStream(
            context = appContext,
            url = playbackUrl,
            title = currentTitle,
        )
    ) {
        is ExternalPlayerRouter.PlayResult.Success -> {
            requestPlayerExit("Opened ${currentTitle.ifBlank { "video" }} in external player.")
            true
        }
        else -> false
    }
}
