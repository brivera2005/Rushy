package com.streamvault.app.player.external

import android.content.Context
import android.widget.Toast
import com.streamvault.app.navigation.PlayerNavigationRequest
import com.streamvault.domain.repository.ChannelRepository

internal suspend fun resolveExternalLivePlaybackUrl(
    channelRepository: ChannelRepository,
    request: PlayerNavigationRequest,
): String? {
    val directUrl = request.streamUrl.trim()
    if (directUrl.isNotBlank() && ExternalPlayerLauncher.isExternalPlayerLaunchUrl(directUrl)) {
        return directUrl
    }
    if (request.internalId > 0L) {
        val channel = channelRepository.getChannel(request.internalId) ?: return null
        return channelRepository.getStreamInfo(channel).getOrNull()?.url
    }
    return directUrl.takeIf { it.isNotBlank() }
}

internal suspend fun launchExternalLivePlayer(
    context: Context,
    channelRepository: ChannelRepository,
    request: PlayerNavigationRequest,
) {
    val resolvedUrl = resolveExternalLivePlaybackUrl(channelRepository, request)
    if (resolvedUrl.isNullOrBlank()) {
        Toast.makeText(
            context.applicationContext,
            "Could not resolve stream URL.",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val streamId = request.streamId.takeIf { it > 0L }
        ?: request.internalId.takeIf { it > 0L }
        ?: 0L
    when (
        ExternalPlayerRouter.playLiveChannel(
            context = context,
            url = resolvedUrl,
            title = request.title,
            streamId = streamId,
        )
    ) {
        is ExternalPlayerRouter.PlayResult.Success -> Unit
        ExternalPlayerRouter.PlayResult.OpenedPlayStore -> {
            Toast.makeText(
                context.applicationContext,
                "Install VLC from the Play Store, then try the channel again.",
                Toast.LENGTH_LONG,
            ).show()
        }
        ExternalPlayerRouter.PlayResult.AllFailed -> {
            Toast.makeText(
                context.applicationContext,
                "Could not open an external player for this channel.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
