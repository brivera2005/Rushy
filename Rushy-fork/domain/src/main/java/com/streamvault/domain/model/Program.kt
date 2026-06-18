package com.streamvault.domain.model

data class Program(
    val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val lang: String = "",
    val rating: String? = null,
    val imageUrl: String? = null,
    val genre: String? = null,
    val category: String? = null,
    val hasArchive: Boolean = false,
    val isNowPlaying: Boolean = false,
    val providerId: Long = 0L
) {
    val durationMinutes: Int
        get() = ((endTime - startTime) / 60000).toInt()

    fun progressPercent(currentTimeMillis: Long = System.currentTimeMillis()): Float {
        if (endTime <= startTime || currentTimeMillis < startTime) return 0f
        if (currentTimeMillis >= endTime) return 1f
        return ((currentTimeMillis - startTime).toFloat() / (endTime - startTime).toFloat()).coerceIn(0f, 1f)
    }
}
