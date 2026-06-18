package com.streamvault.data.util

import java.util.Locale

internal fun <T> List<T>.rankSearchResults(rawQuery: String, nameSelector: (T) -> String): List<T> {
    val normalizedQuery = rawQuery.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return this

    val queryTokens = normalizedQuery
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
        .filter { it.length >= 2 }

    if (queryTokens.isEmpty()) return this

    return sortedWith(
        compareBy<T> { item ->
            val normalizedName = nameSelector(item).trim().lowercase(Locale.ROOT)
            relevanceBucket(normalizedName, normalizedQuery, queryTokens)
        }.thenBy { item ->
            val normalizedName = nameSelector(item).trim().lowercase(Locale.ROOT)
            firstMatchPosition(normalizedName, queryTokens)
        }.thenBy { item ->
            nameSelector(item).length
        }.thenBy { item ->
            nameSelector(item).lowercase(Locale.ROOT)
        }
    )
}

private fun relevanceBucket(normalizedName: String, normalizedQuery: String, queryTokens: List<String>): Int {
    return when {
        normalizedName == normalizedQuery -> 0
        normalizedName.startsWith(normalizedQuery) -> 1
        queryTokens.all { token -> normalizedName.startsWith(token) || normalizedName.contains(" $token") } -> 2
        queryTokens.any { token -> normalizedName.contains(token) } -> 3
        else -> 4
    }
}

private fun firstMatchPosition(normalizedName: String, queryTokens: List<String>): Int {
    return queryTokens.minOfOrNull { token ->
        normalizedName.indexOf(token).takeIf { it >= 0 } ?: Int.MAX_VALUE
    } ?: Int.MAX_VALUE
}