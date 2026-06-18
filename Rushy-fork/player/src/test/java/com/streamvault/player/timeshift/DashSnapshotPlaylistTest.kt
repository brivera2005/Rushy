package com.streamvault.player.timeshift

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DashSnapshotPlaylistTest {

    @Test
    fun `buildDashSnapshotPlaylist emits init map for fmp4 snapshots`() {
        val playlist = buildDashSnapshotPlaylist(
            targetDurationSeconds = 6,
            segments = listOf(
                DashSnapshotPlaylistSegment(fileName = "init-0.mp4", durationMs = 0L, isInit = true),
                DashSnapshotPlaylistSegment(fileName = "segment-0.mp4", durationMs = 4_000L, isInit = false),
                DashSnapshotPlaylistSegment(fileName = "segment-1.mp4", durationMs = 5_500L, isInit = false)
            )
        )

        assertThat(playlist).contains("#EXT-X-VERSION:7")
        assertThat(playlist).contains("#EXT-X-MAP:URI=\"init-0.mp4\"")
        assertThat(playlist).contains("#EXTINF:4.000,")
        assertThat(playlist).contains("segment-0.mp4")
        assertThat(playlist).contains("#EXTINF:5.500,")
        assertThat(playlist).contains("segment-1.mp4")
    }

    @Test
    fun `buildDashSnapshotPlaylist omits extinf for init segment`() {
        val playlist = buildDashSnapshotPlaylist(
            targetDurationSeconds = 6,
            segments = listOf(
                DashSnapshotPlaylistSegment(fileName = "init-0.mp4", durationMs = 0L, isInit = true),
                DashSnapshotPlaylistSegment(fileName = "segment-0.mp4", durationMs = 4_000L, isInit = false)
            )
        )

        assertThat(playlist).doesNotContain("#EXTINF:0.000,")
    }
}
