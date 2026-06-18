package com.streamvault.data.local

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ProviderEpgSyncMode
import com.streamvault.domain.model.ProviderStatus
import com.streamvault.domain.model.ProviderType
import org.junit.Test

class RoomEnumConvertersTest {
    private val converters = RoomEnumConverters()

    @Test
    fun `unknown enum tokens map to compatibility defaults instead of throwing`() {
        assertThat(converters.toProviderType("legacy_xtream")).isEqualTo(ProviderType.M3U)
        assertThat(converters.toProviderStatus("legacy_status")).isEqualTo(ProviderStatus.UNKNOWN)
        assertThat(converters.toProviderEpgSyncMode("legacy_mode")).isEqualTo(ProviderEpgSyncMode.SKIP)
        assertThat(converters.toContentType("legacy_content")).isEqualTo(ContentType.LIVE)
    }

    @Test
    fun `known legacy aliases map to the intended enum values`() {
        assertThat(converters.toProviderType("xtream")).isEqualTo(ProviderType.XTREAM_CODES)
        assertThat(converters.toProviderType("stb")).isEqualTo(ProviderType.STALKER_PORTAL)
        assertThat(converters.toProviderEpgSyncMode("disabled")).isEqualTo(ProviderEpgSyncMode.SKIP)
        assertThat(converters.toContentType("episode")).isEqualTo(ContentType.SERIES_EPISODE)
    }

    @Test
    fun `enum parsing remains null safe and case insensitive`() {
        assertThat(converters.toProviderType(null)).isNull()
        assertThat(converters.toProviderStatus("active")).isEqualTo(ProviderStatus.ACTIVE)
        assertThat(converters.toProviderEpgSyncMode("background")).isEqualTo(ProviderEpgSyncMode.BACKGROUND)
        assertThat(converters.toContentType("series_episode")).isEqualTo(ContentType.SERIES_EPISODE)
    }
}