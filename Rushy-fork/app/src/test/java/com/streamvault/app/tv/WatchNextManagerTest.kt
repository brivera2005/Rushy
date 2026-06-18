package com.streamvault.app.tv

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.PlaybackHistory
import org.junit.Test

class WatchNextManagerTest {

    @Test
    fun selectWatchNextHistoryEntries_keepsOnlyActiveProviderRows() {
        val rows = selectWatchNextHistoryEntries(
            activeProviderId = 1L,
            historyEntries = listOf(
                history(contentId = 10L, providerId = 1L, lastWatchedAt = 300L),
                history(contentId = 11L, providerId = 2L, lastWatchedAt = 400L),
                history(contentId = 12L, providerId = 1L, lastWatchedAt = 200L)
            )
        )

        assertThat(rows.map { it.providerId }).containsExactly(1L, 1L)
        assertThat(rows.map { it.contentId }).containsExactly(10L, 12L).inOrder()
    }

    @Test
    fun selectWatchNextHistoryEntries_returnsEmptyWithoutActiveProvider() {
        val rows = selectWatchNextHistoryEntries(
            activeProviderId = null,
            historyEntries = listOf(history(contentId = 10L, providerId = 1L, lastWatchedAt = 300L))
        )

        assertThat(rows).isEmpty()
    }

    private fun history(contentId: Long, providerId: Long, lastWatchedAt: Long): PlaybackHistory = PlaybackHistory(
        contentId = contentId,
        contentType = ContentType.SERIES_EPISODE,
        providerId = providerId,
        title = "Episode $contentId",
        streamUrl = "https://example.com/$contentId.m3u8",
        resumePositionMs = 1_000L,
        totalDurationMs = 10_000L,
        lastWatchedAt = lastWatchedAt
    )
}