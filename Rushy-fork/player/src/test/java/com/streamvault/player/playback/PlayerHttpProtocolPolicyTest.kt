package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.VodHttpProtocolMode
import org.junit.Test

class PlayerHttpProtocolPolicyTest {

    @Test
    fun `progressive vod streams force http1 in compatibility mode`() {
        assertThat(
            PlayerHttpProtocolPolicy.forceHttp1(
                resolvedStreamType = ResolvedStreamType.PROGRESSIVE,
                vodHttpProtocolMode = VodHttpProtocolMode.COMPATIBILITY_HTTP1
            )
        )
            .isTrue()
    }

    @Test
    fun `progressive vod streams keep default protocol negotiation in auto mode`() {
        assertThat(
            PlayerHttpProtocolPolicy.forceHttp1(
                resolvedStreamType = ResolvedStreamType.PROGRESSIVE,
                vodHttpProtocolMode = VodHttpProtocolMode.AUTO
            )
        )
            .isFalse()
    }

    @Test
    fun `live and adaptive streams keep default protocol negotiation even in compatibility mode`() {
        assertThat(
            PlayerHttpProtocolPolicy.forceHttp1(
                resolvedStreamType = ResolvedStreamType.HLS,
                vodHttpProtocolMode = VodHttpProtocolMode.COMPATIBILITY_HTTP1
            )
        )
            .isFalse()
        assertThat(
            PlayerHttpProtocolPolicy.forceHttp1(
                resolvedStreamType = ResolvedStreamType.DASH,
                vodHttpProtocolMode = VodHttpProtocolMode.COMPATIBILITY_HTTP1
            )
        )
            .isFalse()
        assertThat(
            PlayerHttpProtocolPolicy.forceHttp1(
                resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
                vodHttpProtocolMode = VodHttpProtocolMode.COMPATIBILITY_HTTP1
            )
        )
            .isFalse()
    }
}
