package com.streamvault.player.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.streamvault.domain.model.DecoderMode
import com.streamvault.domain.model.PlaybackCompatibilityRecord
import java.util.Locale

enum class ActiveDecoderPolicy {
    AUTO,
    HARDWARE_PREFERRED,
    SOFTWARE_PREFERRED,
    COMPATIBILITY
}

internal fun shouldUseManagedCodecSelector(
    requestedMode: DecoderMode,
    decoderPolicy: ActiveDecoderPolicy
): Boolean = requestedMode != DecoderMode.AUTO && decoderPolicy != ActiveDecoderPolicy.AUTO

internal data class PlaybackRendererPlan(
    val useAudioVideoSyncSink: Boolean,
    val useVideoRendererWorkaround: Boolean,
    val useManagedCodecSelector: Boolean,
    val extensionRendererMode: PlaybackExtensionRendererMode,
    val renderPath: String
) {
    val useStockRenderersFactory: Boolean
        get() = !useAudioVideoSyncSink && !useVideoRendererWorkaround
}

internal enum class PlaybackExtensionRendererMode {
    PLATFORM_FIRST,
    EXTENSIONS_FIRST
}

internal fun extensionRendererModeFor(activeDecoderMode: DecoderMode): PlaybackExtensionRendererMode {
    return when (activeDecoderMode) {
        DecoderMode.AUTO,
        DecoderMode.HARDWARE -> PlaybackExtensionRendererMode.PLATFORM_FIRST
        DecoderMode.SOFTWARE,
        DecoderMode.COMPATIBILITY -> PlaybackExtensionRendererMode.EXTENSIONS_FIRST
    }
}

internal fun buildPlaybackRendererPlan(
    requestedMode: DecoderMode,
    activeDecoderMode: DecoderMode,
    decoderPolicy: ActiveDecoderPolicy,
    useAudioVideoSyncSink: Boolean,
    useVideoRendererWorkaround: Boolean
): PlaybackRendererPlan {
    val useManagedCodecSelector = shouldUseManagedCodecSelector(requestedMode, decoderPolicy)
    val extensionRendererMode = extensionRendererModeFor(activeDecoderMode)
    val renderPath = buildList {
        if (useAudioVideoSyncSink) add("av-sync-sink")
        if (useVideoRendererWorkaround) add("decoder-reuse-workaround")
        if (useManagedCodecSelector) add("managed-codec-selector")
        if (extensionRendererMode == PlaybackExtensionRendererMode.PLATFORM_FIRST) {
            add("platform-first-extension-audio-fallback")
        }
    }.ifEmpty { listOf("stock-media3") }.joinToString("+")
    return PlaybackRendererPlan(
        useAudioVideoSyncSink = useAudioVideoSyncSink,
        useVideoRendererWorkaround = useVideoRendererWorkaround,
        useManagedCodecSelector = useManagedCodecSelector,
        extensionRendererMode = extensionRendererMode,
        renderPath = renderPath
    )
}

@UnstableApi
class PlaybackCodecSelector(
    private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    private val policyProvider: () -> ActiveDecoderPolicy,
    private val knownBadProvider: () -> Set<String>
) : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): MutableList<MediaCodecInfo> {
        val infos = delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val knownBad = knownBadProvider().map { it.lowercase(Locale.ROOT) }.toSet()
        val policy = policyProvider()
        return infos.sortedWith(
            compareBy<MediaCodecInfo> { info ->
                if (info.name.lowercase(Locale.ROOT) in knownBad) 1 else 0
            }.thenBy { info ->
                when (policy) {
                    ActiveDecoderPolicy.AUTO -> 0
                    ActiveDecoderPolicy.HARDWARE_PREFERRED -> if (isSoftwareCodec(info.name)) 1 else 0
                    ActiveDecoderPolicy.SOFTWARE_PREFERRED,
                    ActiveDecoderPolicy.COMPATIBILITY -> if (isSoftwareCodec(info.name)) 0 else 1
                }
            }
        ).toMutableList()
    }

    companion object {
        fun isSoftwareCodec(name: String): Boolean {
            val normalized = name.lowercase(Locale.ROOT)
            return normalized.startsWith("omx.google.") ||
                normalized.startsWith("c2.android.") ||
                normalized.contains("ffmpeg") ||
                normalized.contains("avcodec")
        }

        fun knownBadDecoderNames(records: List<PlaybackCompatibilityRecord>): Set<String> =
            records.filter(PlaybackCompatibilityRecord::isKnownBad)
                .map { it.key.decoderName }
                .filter { it.isNotBlank() }
                .toSet()
    }
}

