package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.DecoderMode
import org.junit.Test

class FfmpegAudioFallbackPolicyTest {

    @Test
    fun `auto with supported mime and available extension does not reprepare in software`() {
        assertThat(
            shouldAttemptFfmpegAudioFallback(
                FfmpegAudioFallbackRequest(
                    requestedMode = DecoderMode.AUTO,
                    extensionAvailable = true,
                    supportedMimeTypes = listOf("audio/ac3"),
                    fallbackMode = DecoderMode.SOFTWARE
                )
            )
        ).isFalse()
    }

    @Test
    fun `fallback is skipped when extension is unavailable`() {
        assertThat(
            shouldAttemptFfmpegAudioFallback(
                FfmpegAudioFallbackRequest(
                    requestedMode = DecoderMode.AUTO,
                    extensionAvailable = false,
                    supportedMimeTypes = listOf("audio/ac3"),
                    fallbackMode = DecoderMode.SOFTWARE
                )
            )
        ).isFalse()
    }

    @Test
    fun `fallback is skipped for explicit software and compatibility modes`() {
        val software = shouldAttemptFfmpegAudioFallback(
            FfmpegAudioFallbackRequest(
                requestedMode = DecoderMode.SOFTWARE,
                extensionAvailable = true,
                supportedMimeTypes = listOf("audio/ac3"),
                fallbackMode = DecoderMode.SOFTWARE
            )
        )
        val compatibility = shouldAttemptFfmpegAudioFallback(
            FfmpegAudioFallbackRequest(
                requestedMode = DecoderMode.COMPATIBILITY,
                extensionAvailable = true,
                supportedMimeTypes = listOf("audio/ac3"),
                fallbackMode = DecoderMode.SOFTWARE
            )
        )

        assertThat(software).isFalse()
        assertThat(compatibility).isFalse()
    }

    @Test
    fun `fallback is skipped when no supported mime types remain`() {
        assertThat(
            shouldAttemptFfmpegAudioFallback(
                FfmpegAudioFallbackRequest(
                    requestedMode = DecoderMode.AUTO,
                    extensionAvailable = true,
                    supportedMimeTypes = emptyList(),
                    fallbackMode = DecoderMode.SOFTWARE
                )
            )
        ).isFalse()
    }
}
