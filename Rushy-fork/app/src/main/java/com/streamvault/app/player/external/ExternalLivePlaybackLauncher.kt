package com.streamvault.app.player.external

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.streamvault.app.navigation.PlayerNavigationRequest
import com.streamvault.data.remote.xtream.XtreamUrlFactory
import com.streamvault.domain.repository.ChannelRepository

internal suspend fun resolveExternalLivePlaybackUrl(
    channelRepository: ChannelRepository,
    request: PlayerNavigationRequest,
): String? {
    if (request.internalId > 0L) {
        val channel = channelRepository.getChannel(request.internalId) ?: return null
        channelRepository.getStreamInfo(channel, preferStableUrl = true)
            .getOrNull()
            ?.url
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    val directUrl = request.streamUrl.trim()
    if (directUrl.isNotBlank() && ExternalPlayerLauncher.isExternalPlayerLaunchUrl(directUrl)) {
        return directUrl
    }
    return null
}

internal suspend fun resolveExternalLiveStreamId(
    channelRepository: ChannelRepository,
    request: PlayerNavigationRequest,
): Long {
    if (request.internalId > 0L) {
        val channel = channelRepository.getChannel(request.internalId)
        if (channel != null) {
            channel.streamId.takeIf { it > 0L }?.let { return it }
            XtreamUrlFactory.parseInternalStreamUrl(channel.streamUrl)
                ?.streamId
                ?.takeIf { it > 0L }
                ?.let { return it }
        }
    }
    val candidate = request.streamId.takeIf { it > 0L } ?: 0L
    return candidate.takeUnless { it == request.internalId && request.internalId > 0L } ?: 0L
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
    val streamId = resolveExternalLiveStreamId(channelRepository, request)
    when (
        ExternalPlayerRouter.playLiveChannel(
            context = context,
            url = resolvedUrl,
            title = request.title,
            streamId = streamId,
        )
    ) {
        is ExternalPlayerRouter.PlayResult.Success -> {
            (context as? Activity)?.finish()
        }
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
                "Could not open TiviMate for this channel.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
