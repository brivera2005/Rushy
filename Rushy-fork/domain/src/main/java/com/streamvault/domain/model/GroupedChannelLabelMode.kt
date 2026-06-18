package com.streamvault.domain.model

enum class GroupedChannelLabelMode(val storageValue: String) {
    CANONICAL("canonical"),
    ORIGINAL_PROVIDER_LABEL("original_provider_label"),
    HYBRID("hybrid");

    companion object {
        fun fromStorage(value: String?): GroupedChannelLabelMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: HYBRID
    }
}
