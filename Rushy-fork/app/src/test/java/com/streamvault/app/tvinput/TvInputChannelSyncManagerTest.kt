package com.streamvault.app.tvinput

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import org.junit.Test

class TvInputChannelSyncManagerTest {

    @Test
    fun shouldReplaceTvPrograms_preservesGuideWhenMappedChannelHasEmptySnapshot() {
        val channel = Channel(
            id = 1L,
            name = "News",
            providerId = 2L,
            epgChannelId = "news-hd"
        )

        assertThat(shouldReplaceTvPrograms(channel, emptyList())).isFalse()
    }

    @Test
    fun shouldReplaceTvPrograms_allowsReplacementWhenFreshProgramsExist() {
        val channel = Channel(
            id = 1L,
            name = "News",
            providerId = 2L,
            epgChannelId = "news-hd"
        )
        val programs = listOf(
            Program(
                channelId = "news-hd",
                title = "Morning News",
                startTime = 1_000L,
                endTime = 2_000L
            )
        )

        assertThat(shouldReplaceTvPrograms(channel, programs)).isTrue()
    }

    @Test
    fun shouldReplaceTvPrograms_allowsClearingWhenChannelHasNoGuideIdentity() {
        val channel = Channel(
            id = 1L,
            name = "No EPG",
            providerId = 2L,
            epgChannelId = null
        )

        assertThat(shouldReplaceTvPrograms(channel, emptyList())).isTrue()
    }
}