package com.streamvault.app.bootstrap

import com.streamvault.app.DefaultCredentials
import com.streamvault.data.remote.plex.PlexClient
import com.streamvault.data.remote.plex.PlexCredentialStore
import com.streamvault.data.util.ProviderInputSanitizer
import com.streamvault.domain.model.Provider
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.XtreamProviderSetupCommand
import java.net.URI
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

        removeStaleXtreamProviders(providerRepository.getProviders().first())
        ensureXtreamProvider(providerRepository.getProviders().first())
        ensurePlexProvider(providerRepository.getProviders().first())
        ensureDualProviderMode()
    }

    private suspend fun removeStaleXtreamProviders(providers: List<Provider>) {
        val targetPortal = portalHostKey(DefaultCredentials.PORTAL_URL) ?: return
        val targetUser = ProviderInputSanitizer.normalizeUsername(DefaultCredentials.USERNAME)
        providers.filter { provider ->
            provider.type == ProviderType.XTREAM_CODES &&
                portalHostKey(provider.serverUrl) == targetPortal &&
                ProviderInputSanitizer.normalizeUsername(provider.username) != targetUser
        }.forEach { stale ->
            providerRepository.deleteProvider(stale.id)
        }
    }

    private suspend fun ensureXtreamProvider(providers: List<Provider>) {
        if (DefaultCredentials.PORTAL_URL.isBlank() ||
            DefaultCredentials.USERNAME.isBlank() ||
            DefaultCredentials.PASSWORD.isBlank()
        ) {
            return
        }
        val targetPortal = portalHostKey(DefaultCredentials.PORTAL_URL) ?: return
        val targetUser = ProviderInputSanitizer.normalizeUsername(DefaultCredentials.USERNAME)
        val existing = providers.firstOrNull { provider ->
            provider.type == ProviderType.XTREAM_CODES &&
                portalHostKey(provider.serverUrl) == targetPortal &&
                ProviderInputSanitizer.normalizeUsername(provider.username) == targetUser
        }

        validateAndAddProvider.loginXtream(
            XtreamProviderSetupCommand(
                serverUrl = DefaultCredentials.PORTAL_URL,
                username = DefaultCredentials.USERNAME,
                password = DefaultCredentials.PASSWORD,
                name = existing?.name?.takeIf { it.isNotBlank() } ?: "Rushy IPTV",
                xtreamFastSyncEnabled = true,
                existingProviderId = existing?.id,
            ),
        )
    }

    private suspend fun ensurePlexProvider(providers: List<Provider>) {
        if (DefaultCredentials.PLEX_SERVER_URL.isBlank() ||
            DefaultCredentials.PLEX_TOKEN.isBlank()
        ) {
            return
        }
        val normalizedServer = PlexClient.normalizeServerUrl(
            ProviderInputSanitizer.normalizeUrl(DefaultCredentials.PLEX_SERVER_URL),
        )
        val existing = providers.firstOrNull { it.type == ProviderType.PLEX }
        plexCredentialStore.save(normalizedServer, DefaultCredentials.PLEX_TOKEN)
        plexCredentialStore.backupEnabled = true
        providerRepository.loginPlex(
            serverUrl = DefaultCredentials.PLEX_SERVER_URL,
            token = DefaultCredentials.PLEX_TOKEN,
            name = existing?.name?.takeIf { it.isNotBlank() } ?: "Plex Backup",
            id = existing?.id,
        )
    }

    private fun portalHostKey(url: String): String? {
        if (url.isBlank()) return null
        val normalized = ProviderInputSanitizer.normalizeUrl(url.trim()).trimEnd('/')
        val withScheme = if (normalized.contains("://")) normalized else "http://$normalized"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            val port = when {
                uri.port != -1 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            "$host:$port"
        }.getOrNull()
    }

    /** Keeps Xtream as the live provider and Plex synced as always-on VOD backup. */
    private suspend fun ensureDualProviderMode() {
        val providers = providerRepository.getProviders().first()
        val liveProvider = providers.firstOrNull { it.type in LIVE_PROVIDER_TYPES } ?: return
        val activeLive = providerRepository.getActiveProvider().first()
        if (activeLive == null || activeLive.type == ProviderType.PLEX) {
            providerRepository.setActiveProvider(liveProvider.id)
        } else if (activeLive.type in LIVE_PROVIDER_TYPES && activeLive.id != liveProvider.id) {
            providerRepository.setActiveProvider(liveProvider.id)
        }
        providers.firstOrNull { it.type == ProviderType.PLEX }?.let { plex ->
            providerRepository.setBackupProvider(plex.id)
        }
    }
}
