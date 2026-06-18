package com.streamvault.data.epg

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes channel display names for EPG matching.
 * Designed for comparing channel names across different EPG sources.
 *
 * This is intentionally simpler than [ChannelNormalizer.getLogicalGroupId]
 * — it does not strip quality tags or country prefixes because those may be
 * meaningful when matching EPG channels to provider channels.
 */
object EpgNameNormalizer {

    private val nonAlphanumericRegex = Regex("[^a-z0-9]")

    /**
     * Produces a normalized key from a channel display name.
     * - lowercase
     * - strip accents
     * - remove all non-alphanumeric characters
     *
     * Example: "BBC One HD" → "bbconehd"
     */
    fun normalize(name: String): String {
        if (name.isBlank()) return ""
        val lower = name.lowercase(Locale.ROOT)
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return stripped.replace(nonAlphanumericRegex, "")
    }
}
