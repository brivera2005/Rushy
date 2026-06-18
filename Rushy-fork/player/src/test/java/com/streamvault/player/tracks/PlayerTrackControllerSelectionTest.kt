package com.streamvault.player.tracks

import com.google.common.truth.Truth.assertThat
import com.streamvault.player.PLAYER_TRACK_AUTO_ID
import org.junit.Test

class PlayerTrackControllerSelectionTest {

    @Test
    fun `stale selected video track falls back to auto when multiple tracks remain`() {
        assertThat(
            normalizeSelectedVideoTrackId(
                selectedTrackId = "stale-id",
                availableTrackIds = listOf("720p", "1080p")
            )
        ).isEqualTo(PLAYER_TRACK_AUTO_ID)
    }

    @Test
    fun `stale selected video track falls back to only available track`() {
        assertThat(
            normalizeSelectedVideoTrackId(
                selectedTrackId = "stale-id",
                availableTrackIds = listOf("1080p")
            )
        ).isEqualTo("1080p")
    }

    @Test
    fun `current selected video track is preserved when still available`() {
        assertThat(
            normalizeSelectedVideoTrackId(
                selectedTrackId = "720p",
                availableTrackIds = listOf("720p", "1080p")
            )
        ).isEqualTo("720p")
    }
}