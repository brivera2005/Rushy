package com.streamvault.domain.model

enum class LiveTvPlayerMode(val storageValue: String) {
    INTERNAL("internal"),
    /** Default: built-in ExoPlayer; TiviMate remains available in Settings. */
    TIVIMATE_ALWAYS("tivimate_always"),
    TIVIMATE("tivimate"),
    TIVIMATE_ON_STALL("tivimate_on_stall"),
    EXTERNAL("external");

    val prefersTiviMateFirst: Boolean
        get() = this == TIVIMATE_ALWAYS || this == TIVIMATE || this == TIVIMATE_ON_STALL

    companion object {
        fun fromStorageValue(value: String?): LiveTvPlayerMode {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) return INTERNAL
            return entries.firstOrNull { it.storageValue.equals(trimmed, ignoreCase = true) } ?: INTERNAL
        }
    }
}
