package com.streamvault.app.ui.screens.epg

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun startOfGuideDay(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val localDate = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

internal fun shiftGuideDayStart(
    dayStartMillis: Long,
    days: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val localDate = Instant.ofEpochMilli(dayStartMillis).atZone(zoneId).toLocalDate().plusDays(days)
    return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

internal fun shiftGuideAnchorByDays(
    anchorTimeMillis: Long,
    days: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = Instant.ofEpochMilli(anchorTimeMillis)
    .atZone(zoneId)
    .plusDays(days)
    .toInstant()
    .toEpochMilli()

internal fun guidePrimeTimeAnchor(
    anchorTimeMillis: Long,
    primeTimeHour: Int,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val localDate = Instant.ofEpochMilli(anchorTimeMillis).atZone(zoneId).toLocalDate()
    return localDate.atTime(primeTimeHour, 0).atZone(zoneId).toInstant().toEpochMilli()
}

internal fun jumpGuideAnchorToDay(
    anchorTimeMillis: Long,
    dayStartMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val anchorDateTime = Instant.ofEpochMilli(anchorTimeMillis).atZone(zoneId)
    val targetDate = Instant.ofEpochMilli(dayStartMillis).atZone(zoneId).toLocalDate()
    return targetDate.atTime(anchorDateTime.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
}

internal fun dayRelativeOffset(
    dayStartMillis: Long,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val day = Instant.ofEpochMilli(dayStartMillis).atZone(zoneId).toLocalDate()
    return day.toEpochDay() - today.toEpochDay()
}

internal fun previousGuideHalfHour(
    timestamp: Long,
    stepMs: Long = 30 * 60 * 1000L,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val stepMinutes = (stepMs / 60_000L).coerceAtLeast(1L).toInt()
    val zdt = Instant.ofEpochMilli(timestamp).atZone(zoneId)
    val totalMinutes = zdt.hour * 60 + zdt.minute
    val alignedMinutes = (totalMinutes / stepMinutes) * stepMinutes
    return zdt.withHour(alignedMinutes / 60)
        .withMinute(alignedMinutes % 60)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli()
}

internal fun guideTimelineMarkers(
    windowStart: Long,
    windowEnd: Long,
    stepMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    includeWindowEnd: Boolean = false
): List<Long> = buildList {
    if (windowEnd <= windowStart || stepMs <= 0L) return@buildList
    var marker = previousGuideHalfHour(windowStart, stepMs, zoneId)
    while (marker <= windowEnd) {
        add(marker)
        marker += stepMs
    }
    if (includeWindowEnd && lastOrNull() != windowEnd) {
        add(windowEnd)
    }
}