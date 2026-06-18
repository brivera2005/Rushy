package com.streamvault.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalLanguageSearchQueryBuilderTest {

    @Test
    fun shortQueriesAreNotConversational() {
        assertThat(NaturalLanguageSearchQueryBuilder.isConversational("the matrix")).isFalse()
    }

    @Test
    fun longQueriesAreConversational() {
        assertThat(
            NaturalLanguageSearchQueryBuilder.isConversational(
                "whats that movie with jim carrey and matthew broderick"
            )
        ).isTrue()
    }

    @Test
    fun buildQueriesPrefersProbableTitleFirst() {
        val hints = NaturalLanguageSearchHints(
            probableTitle = "The Cable Guy",
            keywords = listOf("jim carrey", "matthew broderick", "cable"),
            isConversational = true
        )

        val queries = NaturalLanguageSearchQueryBuilder.buildQueries(
            "whats that movie with jim carrey and matthew broderick",
            hints
        )

        assertThat(queries.first()).isEqualTo("The Cable Guy")
        assertThat(queries).contains("jim carrey matthew broderick cable")
    }

    @Test
    fun nonConversationalQueriesStaySingle() {
        val queries = NaturalLanguageSearchQueryBuilder.buildQueries(
            "cable guy",
            NaturalLanguageSearchHints(isConversational = false)
        )

        assertThat(queries).containsExactly("cable guy")
    }
}
