package com.streamvault.player.playback

import com.streamvault.domain.model.VodHttpProtocolMode

object PlayerHttpProtocolPolicy {
    fun forceHttp1(
        resolvedStreamType: ResolvedStreamType,
        vodHttpProtocolMode: VodHttpProtocolMode
    ): Boolean {
        return resolvedStreamType == ResolvedStreamType.PROGRESSIVE &&
            vodHttpProtocolMode == VodHttpProtocolMode.COMPATIBILITY_HTTP1
    }
}
