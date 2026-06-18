package com.streamvault.domain.model

enum class LiveChannelGroupingMode(val storageValue: String) {
    GROUPED("grouped"),
    RAW_VARIANTS("raw_variants");

    companion object {
        fun fromStorage(value: String?): LiveChannelGroupingMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: RAW_VARIANTS
    }
}
