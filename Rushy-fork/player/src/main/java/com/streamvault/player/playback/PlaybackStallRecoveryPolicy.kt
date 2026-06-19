package com.streamvault.player.playback

import com.streamvault.player.PlaybackState

internal fun shouldRecoverReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    true

internal fun shouldRecoverPositionAdvancingReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    !resolvedStreamType.isLiveForStallRecovery

internal fun shouldRecoverFrameSilentReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    resolvedStreamType.isLiveForStallRecovery

private const val MAX_LIVE_STALL_RECONNECT_ATTEMPTS = 3

internal fun shouldReconnectLiveStall(
    playbackState: PlaybackState,
    resolvedStreamType: ResolvedStreamType,
    recoveryAttempt: Int
): Boolean =
    recoveryAttempt in 1..MAX_LIVE_STALL_RECONNECT_ATTEMPTS &&
        (
            playbackState == PlaybackState.BUFFERING && resolvedStreamType.isLiveForStallRecovery ||
                playbackState == PlaybackState.READY && resolvedStreamType.isLiveForStallRecovery
        )

private val ResolvedStreamType.isLiveForStallRecovery: Boolean
    get() = this == ResolvedStreamType.HLS ||
        this == ResolvedStreamType.SMOOTH_STREAMING ||
        this == ResolvedStreamType.MPEG_TS_LIVE ||
        this == ResolvedStreamType.RTSP
