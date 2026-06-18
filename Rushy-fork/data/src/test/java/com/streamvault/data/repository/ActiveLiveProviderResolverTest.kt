package com.streamvault.data.repository

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ProviderStatus
import com.streamvault.domain.model.ProviderType
import org.junit.Test

class ActiveLiveProviderResolverTest {

    @Test
    fun prefersActiveLiveProviderOverActivePlexBackup() {
        val xtream = provider(id = 1L, type = ProviderType.XTREAM_CODES, isActive = true, lastSyncedAt = 100L)
        val plex = provider(id = 2L, type = ProviderType.PLEX, isActive = true, lastSyncedAt = 200L)

        assertThat(resolveActiveLiveProvider(listOf(plex, xtream))?.id).isEqualTo(1L)
    }

    @Test
    fun fallsBackToSyncedLiveProviderWhenNoneMarkedActive() {
        val xtream = provider(
            id = 1L,
            type = ProviderType.XTREAM_CODES,
            isActive = false,
            status = ProviderStatus.ACTIVE,
            lastSyncedAt = 500L,
        )
        val plex = provider(id = 2L, type = ProviderType.PLEX, isActive = true, lastSyncedAt = 900L)

        assertThat(resolveActiveLiveProvider(listOf(plex, xtream))?.id).isEqualTo(1L)
    }

    @Test
    fun returnsPlexWhenItIsTheOnlyProvider() {
        val plex = provider(id = 2L, type = ProviderType.PLEX, isActive = true)

        assertThat(resolveActiveLiveProvider(listOf(plex))?.id).isEqualTo(2L)
    }

    private fun provider(
        id: Long,
        type: ProviderType,
        isActive: Boolean,
        status: ProviderStatus = ProviderStatus.UNKNOWN,
        lastSyncedAt: Long = 0L,
    ) = ProviderEntity(
        id = id,
        name = "Provider $id",
        type = type,
        serverUrl = "https://example.com/$id",
        m3uUrl = "https://example.com/$id.m3u",
        isActive = isActive,
        status = status,
        lastSyncedAt = lastSyncedAt,
    )
}
