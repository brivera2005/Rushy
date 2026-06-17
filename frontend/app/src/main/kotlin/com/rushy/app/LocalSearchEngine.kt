package com.rushy.app

object LocalSearchEngine {

    fun search(catalog: List<MediaItem>, query: String): SearchResult {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) {
            return SearchResult()
        }

        val visible = catalog.filterNot { it.isHidden }
        val scored = visible.map { item ->
            val normalizedTitle = normalize(item.title)
            val score = scoreMatch(normalizedTitle, normalizedQuery)
            item to score
        }.filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }

        val exactMatches = scored
            .filter { (_, score) -> score >= 900 }
            .map { (item, _) -> item }

        val nearMatches = scored
            .filter { (_, score) -> score in 400 until 900 }
            .map { (item, _) -> item }

        return SearchResult(
            exactMatches = exactMatches,
            nearMatches = nearMatches,
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun scoreMatch(title: String, query: String): Int {
        if (title == query) return 1000
        if (title.startsWith(query)) return 950
        if (title.contains(query)) return 900

        val queryTokens = query.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isNotEmpty() && queryTokens.all { title.contains(it) }) {
            return 850
        }

        val titleTokens = title.split(" ").filter { it.isNotBlank() }
        val overlap = queryTokens.count { qt ->
            titleTokens.any { tt -> tt.startsWith(qt) || levenshtein(qt, tt) <= 1 }
        }
        if (overlap > 0) {
            return 400 + (overlap * 100)
        }

        val distance = levenshtein(title, query)
        val maxLen = maxOf(title.length, query.length).coerceAtLeast(1)
        val similarity = 1.0 - (distance.toDouble() / maxLen)
        return if (similarity >= 0.6) (similarity * 350).toInt() else 0
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var last = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    last + cost,
                )
                last = temp
            }
        }
        return costs[b.length]
    }
}
