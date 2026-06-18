package com.streamvault.data.repository

import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ProviderStatus
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.isLiveIptvProvider

/**
 * Resolves the live IPTV provider the app should treat as primary.
 *
 * Dual-provider mode (v2.3+) can leave Xtream inactive in the DB while Plex backup stays
 * active, or mark neither active after a partial sync. Prefer any synced live provider over
 * returning null so Home/Dashboard never show onboarding when a playlist exists.
 */
internal fun resolveActiveLiveProvider(entities: List<ProviderEntity>): ProviderEntity? {
    if (entities.isEmpty()) return null

    val activeProviders = entities.filter { it.isActive }

    activeProviders
        .filter { it.type.isLiveIptvProvider() }
        .minByOrNull { it.id }
        ?.let { return it }

    entities
        .filter { it.type.isLiveIptvProvider() }
        .sortedWith(
            compareByDescending<ProviderEntity> { it.isActive }
                .thenByDescending { it.lastSyncedAt }
                .thenByDescending { it.status == ProviderStatus.ACTIVE }
                .thenByDescending { it.status == ProviderStatus.PARTIAL }
                .thenByDescending { it.id }
        )
        .firstOrNull()
        ?.let { return it }

    return activeProviders
        .sortedWith(
            compareBy<ProviderEntity> { if (it.type == ProviderType.PLEX) 1 else 0 }
                .thenBy { it.id }
        )
        .firstOrNull()
        ?: entities.firstOrNull()
}
