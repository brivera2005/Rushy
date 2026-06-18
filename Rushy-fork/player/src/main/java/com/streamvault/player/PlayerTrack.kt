package com.streamvault.player

data class PlayerTrack(
    val id: String,
    val name: String,
    val language: String?,
    val type: TrackType,
    val isSelected: Boolean
)

enum class TrackType {
    AUDIO,
    TEXT, // Subtitles
    VIDEO
}
