package com.streamvault.app.bootstrap

import com.streamvault.app.DefaultCredentials
import com.streamvault.data.remote.plex.PlexCredentialStore
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.XtreamProviderSetupCommand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class RushyCredentialsBootstrap @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val validateAndAddProvider: ValidateAndAddProvider,
    private val plexCredentialStore: PlexCredentialStore,
) {
    private companion object {
        val LIVE_PROVIDER_TYPES = setOf(
            ProviderType.XTREAM_CODES,
            ProviderType.M3U,
            ProviderType.STALKER_PORTAL,
        )
    }

    suspend fun ensureDefaultCredentialsIfNeeded() {
        if (!DefaultCredentials.AUTO_APPLY) return
        if (providerRepository.getProviders().first().isNotEmpty()) {
            ensureDualProviderMode()
            return
        }

        if (DefaultCredentials.PORTAL_URL.isNotBlank() &&
            DefaultCredentials.USERNAME.isNotBlank() &&
            DefaultCredentials.PASSWORD.isNotBlank()
        ) {
            validateAndAddProvider.loginXtream(
                XtreamProviderSetupCommand(
                    serverUrl = DefaultCredentials.PORTAL_URL,
                    username = DefaultCredentials.USERNAME,
                    password = DefaultCredentials.PASSWORD,
                    name = "Rushy IPTV",
                    xtreamFastSyncEnabled = true,
                ),
            )
        }

        if (DefaultCredentials.PLEX_SERVER_URL.isNotBlank() &&
            DefaultCredentials.PLEX_TOKEN.isNotBlank()
        ) {
            plexCredentialStore.save(
                DefaultCredentials.PLEX_SERVER_URL,
                DefaultCredentials.PLEX_TOKEN,
            )
            plexCredentialStore.backupEnabled = true
            providerRepository.loginPlex(
                serverUrl = DefaultCredentials.PLEX_SERVER_URL,
                token = DefaultCredentials.PLEX_TOKEN,
                name = "Plex Backup",
            )
        }

        ensureDualProviderMode()
    }

    /** Keeps Xtream as the live provider and Plex synced as always-on VOD backup. */
    private suspend fun ensureDualProviderMode() {
        val providers = providerRepository.getProviders().first()
        val liveProvider = providers.firstOrNull { it.type in LIVE_PROVIDER_TYPES } ?: return
        val activeLive = providerRepository.getActiveProvider().first()
        if (activeLive?.id != liveProvider.id) {
            providerRepository.setActiveProvider(liveProvider.id)
        }
        providers.firstOrNull { it.type == ProviderType.PLEX && !it.isActive }?.let { plex ->
            providerRepository.setBackupProvider(plex.id)
        }
    }
}
