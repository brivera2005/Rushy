package com.streamvault.data.util

import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

object AdultContentClassifier {
    private const val MAX_CLASSIFICATION_CACHE_SIZE = 4096
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[^a-z0-9+]+")

    // Strong tags are matched via normalized substring contains (case-insensitive by normalization).
    private val strongContainsKeywords = listOf(
        "xxx",
        "18+",
        "+18",
        "+ 18",
        "18 +",
        "18 plus",
        "plus 18",
        "18plus"
    )

    // Softer tags are matched on normalized word boundaries to avoid overmatching.
    private val boundaryKeywords = listOf(
        // English
        "adult",
        "adults",
        "porn",
        "porno",
        "milf",
        "gay",
        "lesbian",
        "sex",
        "erotic",
        "hustler",
        "playboy",
        "hanime",
        "live cam",
        "live cams",
        "redlight",
        "red light",
        // Spanish
        "adulto",
        "adultos",
        // French
        "adulte",
        "adultes",
        "erotique",
        // German
        "erwachsene",
        "erotik",
        // Russian (transliterated)
        "vzroslye",
        "dlya vzroslykh",
        // Arabic (transliterated)
        "lilkbar",
        // Turkish
        "yetiskin",
        // Anime adult content
        "hentai",
        // Common IPTV labels
        "for adults",
        "pour adultes",
        "para adultos"
    )
    private val normalizedStrongKeywords = strongContainsKeywords.map(::normalize).distinct()
    private val normalizedBoundaryKeywords = boundaryKeywords.map(::normalize).distinct()
    private val classificationCache: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > MAX_CLASSIFICATION_CACHE_SIZE
            }
        }
    )

    fun isAdultCategoryName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = normalize(name)
        synchronized(classificationCache) {
            classificationCache[normalized]?.let { return it }
        }
        val normalizedPadded = " $normalized "
        val isAdult = normalizedStrongKeywords.any { keyword ->
            normalized.contains(keyword)
        } || normalizedBoundaryKeywords.any { keyword ->
            normalizedPadded.contains(" $keyword ")
        }
        synchronized(classificationCache) {
            classificationCache[normalized] = isAdult
        }
        return isAdult
    }

    fun adultCategoryIds(namesById: Map<Long, String>): Set<Long> {
        return namesById
            .filterValues(::isAdultCategoryName)
            .keys
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(diacriticsRegex, "")
            .lowercase(Locale.ROOT)
            .replace(separatorsRegex, " ")
            .trim()
    }

    internal fun resetCacheForTesting() {
        synchronized(classificationCache) {
            classificationCache.clear()
        }
    }

    internal fun cacheSizeForTesting(): Int =
        synchronized(classificationCache) { classificationCache.size }

    internal fun isCachedForTesting(name: String): Boolean =
        synchronized(classificationCache) { classificationCache.containsKey(normalize(name)) }
}
