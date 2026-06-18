package com.streamvault.data.remote.stalker

import com.streamvault.domain.model.StalkerPortalProfile
import com.streamvault.domain.model.StalkerCookieMode
import com.streamvault.domain.model.StalkerPlaybackBackendHint

internal interface StalkerPlaybackAdapter {
    val adapterMode: StalkerPlaybackMode

    fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean

    fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean

    fun requiresCreateLink(variant: StalkerCommandVariant): Boolean

    fun allowsRebootstrap(
        descriptor: StalkerPlaybackDescriptor,
        accountProfile: StalkerProviderProfile
    ): Boolean = descriptor.capabilities.usesTemporaryLinks ||
        accountProfile.ambiguousState ||
        descriptor.primaryMode == StalkerPlaybackMode.MULTI_CMD
}

internal object DirectOrCreateLinkAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.DIRECT_URL

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.DIRECT_URL ||
        variant.playbackMode == StalkerPlaybackMode.LOCALHOST_CMD ||
        descriptor.primaryMode == StalkerPlaybackMode.MULTI_CMD

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean =
        variant.playbackMode == StalkerPlaybackMode.DIRECT_URL

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean =
        variant.playbackMode != StalkerPlaybackMode.DIRECT_URL
}

internal object CookieAwareDirectAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.DIRECT_URL

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.DIRECT_URL &&
        backendHint == StalkerPlaybackBackendHint.DIRECT &&
        cookieModeHint in setOf(StalkerCookieMode.PLAYBACK, StalkerCookieMode.BOTH)

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean = false
}

internal object PlayLivePortalAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.PLAY_LIVE_PORTAL

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.PLAY_LIVE_PORTAL ||
        backendHint == StalkerPlaybackBackendHint.PLAY_LIVE

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean =
        variant.playbackMode != StalkerPlaybackMode.PLAY_LIVE_PORTAL
}

internal object PlayMoviePortalAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.PLAY_MOVIE_PORTAL

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.PLAY_MOVIE_PORTAL ||
        backendHint == StalkerPlaybackBackendHint.PLAY_MOVIE

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean =
        variant.playbackMode != StalkerPlaybackMode.PLAY_MOVIE_PORTAL
}

internal object NginxSecureLinkAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.TEMP_LINK_NGINX

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.TEMP_LINK_NGINX ||
        descriptor.capabilities.nginxSecureLink ||
        descriptor.capabilities.useHttpTemporaryLink ||
        portalProfileHint == StalkerPortalProfile.MAG_STRICT

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean = true
}

internal object FlussonicTemporaryLinkAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.TEMP_LINK_FLUSSONIC

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.TEMP_LINK_FLUSSONIC ||
        descriptor.capabilities.flussonicTemporaryLink

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean = true
}

internal object WowzaTemporaryLinkAdapter : StalkerPlaybackAdapter {
    override val adapterMode: StalkerPlaybackMode = StalkerPlaybackMode.TEMP_LINK_WOWZA

    override fun matches(
        descriptor: StalkerPlaybackDescriptor,
        variant: StalkerCommandVariant,
        portalProfileHint: StalkerPortalProfile,
        preferredMode: StalkerPlaybackMode?,
        backendHint: StalkerPlaybackBackendHint,
        cookieModeHint: StalkerCookieMode
    ): Boolean = variant.playbackMode == StalkerPlaybackMode.TEMP_LINK_WOWZA ||
        descriptor.capabilities.wowzaTemporaryLink

    override fun allowsDirectBypass(variant: StalkerCommandVariant): Boolean = true

    override fun requiresCreateLink(variant: StalkerCommandVariant): Boolean = true
}

internal fun resolveStalkerPlaybackAdapter(
    descriptor: StalkerPlaybackDescriptor,
    variant: StalkerCommandVariant,
    portalProfileHint: StalkerPortalProfile,
    preferredMode: StalkerPlaybackMode?,
    backendHint: StalkerPlaybackBackendHint,
    cookieModeHint: StalkerCookieMode
): StalkerPlaybackAdapter {
    val ordered = buildList {
        if (preferredMode != null) {
            adapterForMode(preferredMode)?.let(::add)
        }
        adapterForBackendHint(backendHint)?.let(::add)
        add(
            when (variant.playbackMode) {
                StalkerPlaybackMode.TEMP_LINK_FLUSSONIC -> FlussonicTemporaryLinkAdapter
                StalkerPlaybackMode.TEMP_LINK_WOWZA -> WowzaTemporaryLinkAdapter
                StalkerPlaybackMode.TEMP_LINK_NGINX -> NginxSecureLinkAdapter
                StalkerPlaybackMode.PLAY_LIVE_PORTAL -> PlayLivePortalAdapter
                StalkerPlaybackMode.PLAY_MOVIE_PORTAL -> PlayMoviePortalAdapter
                else -> DirectOrCreateLinkAdapter
            }
        )
        if (cookieModeHint in setOf(StalkerCookieMode.PLAYBACK, StalkerCookieMode.BOTH)) {
            add(CookieAwareDirectAdapter)
        }
        if (portalProfileHint == StalkerPortalProfile.MAG_STRICT) add(NginxSecureLinkAdapter)
        if (descriptor.capabilities.flussonicTemporaryLink) add(FlussonicTemporaryLinkAdapter)
        if (descriptor.capabilities.wowzaTemporaryLink) add(WowzaTemporaryLinkAdapter)
        if (descriptor.capabilities.nginxSecureLink || descriptor.capabilities.useHttpTemporaryLink) {
            add(NginxSecureLinkAdapter)
        }
        add(DirectOrCreateLinkAdapter)
    }.distinctBy { it.adapterMode }
    return ordered.firstOrNull { adapter ->
        adapter.matches(descriptor, variant, portalProfileHint, preferredMode, backendHint, cookieModeHint)
    } ?: DirectOrCreateLinkAdapter
}

private fun adapterForMode(mode: StalkerPlaybackMode): StalkerPlaybackAdapter? = when (mode) {
    StalkerPlaybackMode.TEMP_LINK_FLUSSONIC -> FlussonicTemporaryLinkAdapter
    StalkerPlaybackMode.TEMP_LINK_WOWZA -> WowzaTemporaryLinkAdapter
    StalkerPlaybackMode.TEMP_LINK_NGINX -> NginxSecureLinkAdapter
    StalkerPlaybackMode.PLAY_LIVE_PORTAL -> PlayLivePortalAdapter
    StalkerPlaybackMode.PLAY_MOVIE_PORTAL -> PlayMoviePortalAdapter
    StalkerPlaybackMode.DIRECT_URL,
    StalkerPlaybackMode.LOCALHOST_CMD,
    StalkerPlaybackMode.MULTI_CMD -> DirectOrCreateLinkAdapter
}

private fun adapterForBackendHint(hint: StalkerPlaybackBackendHint): StalkerPlaybackAdapter? = when (hint) {
    StalkerPlaybackBackendHint.DIRECT -> CookieAwareDirectAdapter
    StalkerPlaybackBackendHint.PLAY_LIVE -> PlayLivePortalAdapter
    StalkerPlaybackBackendHint.PLAY_MOVIE -> PlayMoviePortalAdapter
    StalkerPlaybackBackendHint.TEMP_LINK,
    StalkerPlaybackBackendHint.TEMP_LINK_STRICT -> NginxSecureLinkAdapter
    StalkerPlaybackBackendHint.AUTO -> null
}
