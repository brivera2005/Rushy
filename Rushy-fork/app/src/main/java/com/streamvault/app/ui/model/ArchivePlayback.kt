package com.streamvault.app.ui.model

import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program

private const val MILLIS_PER_DAY = 86_400_000L

fun Channel.isArchivePlayable(
    program: Program,
    now: Long = System.currentTimeMillis()
): Boolean {
    if (id <= 0L || providerId <= 0L) return false
    if (program.startTime <= 0L || program.endTime <= program.startTime) return false
    if (program.hasArchive) return true
    if (!catchUpSupported || program.endTime > now) return false

    val catchUpWindowStart = catchUpDays
        .takeIf { it > 0 }
        ?.let { days -> now - (days.toLong() * MILLIS_PER_DAY) }

    return catchUpWindowStart == null || program.startTime >= catchUpWindowStart
}