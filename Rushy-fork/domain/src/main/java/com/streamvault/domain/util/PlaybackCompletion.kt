package com.streamvault.domain.util

const val DEFAULT_PLAYBACK_COMPLETION_THRESHOLD = 0.95f

fun isPlaybackComplete(
    progressMs: Long,
    totalDurationMs: Long,
    threshold: Float = DEFAULT_PLAYBACK_COMPLETION_THRESHOLD
): Boolean {
    if (progressMs <= 0L || totalDurationMs <= 0L) return false
    return progressMs >= (totalDurationMs * threshold).toLong()
}