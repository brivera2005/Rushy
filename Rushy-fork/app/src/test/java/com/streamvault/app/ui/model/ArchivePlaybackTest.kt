package com.streamvault.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import org.junit.Test

class ArchivePlaybackTest {

    @Test
    fun `catch-up channel does not allow future program replay`() {
        val now = 1_000_000L
        val channel = Channel(
            id = 42L,
            name = "News",
            providerId = 7L,
            catchUpSupported = true,
            catchUpDays = 2
        )
        val program = Program(
            channelId = "news",
            title = "Late Bulletin",
            startTime = now + 60_000L,
            endTime = now + 120_000L
        )

        assertThat(channel.isArchivePlayable(program, now)).isFalse()
    }

    @Test
    fun `catch-up channel allows completed program inside catch-up window`() {
        val now = 3 * 86_400_000L
        val channel = Channel(
            id = 42L,
            name = "News",
            providerId = 7L,
            catchUpSupported = true,
            catchUpDays = 2
        )
        val program = Program(
            channelId = "news",
            title = "Morning Update",
            startTime = now - 86_400_000L,
            endTime = now - 30_000L
        )

        assertThat(channel.isArchivePlayable(program, now)).isTrue()
    }

    @Test
    fun `provider archive flag keeps replay enabled without catch-up support`() {
        val channel = Channel(
            id = 42L,
            name = "News",
            providerId = 7L
        )
        val program = Program(
            channelId = "news",
            title = "Curated Replay",
            startTime = 100_000L,
            endTime = 200_000L,
            hasArchive = true
        )

        assertThat(channel.isArchivePlayable(program, now = 150_000L)).isTrue()
    }

    @Test
    fun `catch-up window excludes expired programs`() {
        val now = 5 * 86_400_000L
        val channel = Channel(
            id = 42L,
            name = "News",
            providerId = 7L,
            catchUpSupported = true,
            catchUpDays = 2
        )
        val program = Program(
            channelId = "news",
            title = "Old Bulletin",
            startTime = now - (3 * 86_400_000L),
            endTime = now - (3 * 86_400_000L) + 60_000L
        )

        assertThat(channel.isArchivePlayable(program, now)).isFalse()
    }
}