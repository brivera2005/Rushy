package com.streamvault.domain.model

enum class LiveVariantPreferenceMode(val storageValue: String) {
    BEST_QUALITY("best_quality"),
    OBSERVED_ONLY("observed_only"),
    BALANCED("balanced"),
    STABILITY_FIRST("stability_first");

    companion object {
        fun fromStorage(value: String?): LiveVariantPreferenceMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: BALANCED
    }
}
