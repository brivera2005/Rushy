package com.streamvault.app.ui.screens.epg

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class GuideDateTimeTest {

    @Test
    fun `guide prime time anchor uses selected local day`() {
        val zoneId = ZoneId.of("America/New_York")
        val anchor = LocalDateTime.of(2026, 5, 2, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val primeTime = guidePrimeTimeAnchor(anchor, EpgViewModel.PRIME_TIME_HOUR, zoneId)
        val localPrimeTime = Instant.ofEpochMilli(primeTime).atZone(zoneId).toLocalDateTime()

        assertThat(localPrimeTime).isEqualTo(LocalDateTime.of(2026, 5, 2, 20, 0))
    }

    @Test
    fun `jump guide anchor to day preserves local time across dst transition`() {
        val zoneId = ZoneId.of("America/New_York")
        val anchor = LocalDateTime.of(2026, 3, 7, 23, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val targetDayStart = LocalDate.of(2026, 3, 8)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val jumpedAnchor = jumpGuideAnchorToDay(anchor, targetDayStart, zoneId)
        val localJumpedAnchor = Instant.ofEpochMilli(jumpedAnchor).atZone(zoneId).toLocalDateTime()

        assertThat(localJumpedAnchor).isEqualTo(LocalDateTime.of(2026, 3, 8, 23, 30))
    }

    @Test
    fun `shift guide day start advances by local calendar day across dst`() {
        val zoneId = ZoneId.of("America/New_York")
        val dayStart = LocalDate.of(2026, 3, 8)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val shiftedDayStart = shiftGuideDayStart(dayStart, 1, zoneId)
        val shiftedLocalDate = Instant.ofEpochMilli(shiftedDayStart).atZone(zoneId).toLocalDate()

        assertThat(shiftedLocalDate).isEqualTo(LocalDate.of(2026, 3, 9))
    }

    @Test
    fun `guide timeline markers align to local half hours`() {
        val zoneId = ZoneId.of("Asia/Kathmandu")
        val windowStart = LocalDateTime.of(2026, 5, 2, 12, 17)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val windowEnd = LocalDateTime.of(2026, 5, 2, 14, 5)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val markers = guideTimelineMarkers(
            windowStart = windowStart,
            windowEnd = windowEnd,
            stepMs = EpgViewModel.HALF_HOUR_SHIFT_MS,
            zoneId = zoneId
        )

        assertThat(markers).isNotEmpty()
        markers.forEach { marker ->
            val localTime = Instant.ofEpochMilli(marker).atZone(zoneId).toLocalTime()
            assertThat(localTime.minute % 30).isEqualTo(0)
            assertThat(localTime.second).isEqualTo(0)
        }
        assertThat(markers.first()).isEqualTo(
            LocalDateTime.of(2026, 5, 2, 12, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        )
    }
}