package com.streamvault.data.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchRankingUtilsTest {

    @Test
    fun `rankSearchResults prioritizes exact then prefix then contains matches`() {
        val results = listOf(
            "Ultra Sports Replay",
            "Sports Center",
            "Daily Sports",
            "Sports",
            "Arena"
        )

        val ranked = results.rankSearchResults("sports") { it }

        assertThat(ranked).containsExactly(
            "Sports",
            "Sports Center",
            "Daily Sports",
            "Ultra Sports Replay",
            "Arena"
        ).inOrder()
    }

    @Test
    fun `rankSearchResults prioritizes all token prefix matches over loose contains`() {
        val results = listOf(
            "Star Documentary Wars",
            "Star Wars Chronicles",
            "Galactic Wars of the Stars"
        )

        val ranked = results.rankSearchResults("star wars") { it }

        assertThat(ranked).containsExactly(
            "Star Wars Chronicles",
            "Star Documentary Wars",
            "Galactic Wars of the Stars"
        ).inOrder()
    }
}