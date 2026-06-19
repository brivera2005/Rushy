package com.streamvault.domain.model

enum class LiveTvPlayerMode(val storageValue: String) {
    INTERNAL("internal"),
    /** Default: always hand off live channels to TiviMate before opening the built-in player. */
    TIVIMATE_ALWAYS("tivimate_always"),
    TIVIMATE("tivimate"),
    TIVIMATE_ON_STALL("tivimate_on_stall"),
    EXTERNAL("external");

    val prefersTiviMateFirst: Boolean
        get() = this == TIVIMATE_ALWAYS || this == TIVIMATE || this == TIVIMATE_ON_STALL

    companion object {
        fun fromStorageValue(value: String?): LiveTvPlayerMode {
            val trimmed = value?.trim()
            if (trimmed.isNullOrBlank()) return TIVIMATE_ALWAYS
            return entries.firstOrNull { it.storageValue.equals(trimmed, ignoreCase = true) } ?: TIVIMATE_ALWAYS
        }
    }
}
