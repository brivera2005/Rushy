package com.streamvault.domain.model

enum class LiveTvPlayerMode(val storageValue: String) {
    INTERNAL("internal"),
    TIVIMATE("tivimate"),
    TIVIMATE_ON_STALL("tivimate_on_stall"),
    EXTERNAL("external");

    companion object {
        fun fromStorageValue(value: String?): LiveTvPlayerMode {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) return INTERNAL
            return entries.firstOrNull { it.storageValue.equals(trimmed, ignoreCase = true) } ?: EXTERNAL
        }
    }
}
