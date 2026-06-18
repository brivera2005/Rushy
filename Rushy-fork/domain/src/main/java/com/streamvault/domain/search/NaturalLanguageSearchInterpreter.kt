package com.streamvault.domain.search

import javax.inject.Inject

data class NaturalLanguageSearchHints(
    val probableTitle: String = "",
    val keywords: List<String> = emptyList(),
    val isConversational: Boolean = false
)

interface NaturalLanguageSearchInterpreter {
    suspend fun interpret(query: String): NaturalLanguageSearchHints
}

object NaturalLanguageSearchQueryBuilder {
    fun isConversational(query: String): Boolean =
        query.trim().split(Regex("\\s+")).count { it.isNotBlank() } > 2

    fun buildQueries(original: String, hints: NaturalLanguageSearchHints): List<String> {
        val trimmed = original.trim()
        if (!hints.isConversational || trimmed.length < 2) {
            return listOf(trimmed)
        }

        val queries = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()

        fun addCandidate(candidate: String) {
            val normalized = candidate.trim()
            if (normalized.length < 2) return
            val key = normalized.lowercase()
            if (seenLower.add(key)) {
                queries.add(normalized)
            }
        }

        addCandidate(hints.probableTitle)
        addCandidate(hints.keywords.joinToString(" "))
        addCandidate(trimmed)

        return queries.ifEmpty { listOf(trimmed) }
    }
}

class PassthroughNaturalLanguageSearchInterpreter @Inject constructor() : NaturalLanguageSearchInterpreter {
    override suspend fun interpret(query: String): NaturalLanguageSearchHints {
        return NaturalLanguageSearchHints(
            isConversational = NaturalLanguageSearchQueryBuilder.isConversational(query)
        )
    }
}
