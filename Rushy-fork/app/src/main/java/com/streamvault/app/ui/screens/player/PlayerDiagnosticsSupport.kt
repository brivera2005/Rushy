package com.streamvault.app.ui.screens.player

import com.streamvault.domain.model.Channel

internal fun updateChannelDiagnosticsState(
    currentState: PlayerDiagnosticsUiState,
    channel: Channel
): PlayerDiagnosticsUiState {
    val archiveLabel = when {
        channel.catchUpSupported && (channel.streamId > 0L || !channel.catchUpSource.isNullOrBlank()) ->
            "Catch-up supported (${channel.catchUpDays} days)"
        channel.catchUpSupported ->
            "Provider advertises catch-up, but replay metadata is incomplete."
        else -> "No archive support advertised"
    }
    val hints = buildList {
        if (channel.errorCount > 0) {
            add("This channel has failed ${channel.errorCount} time(s) recently.")
        }
        if (channel.alternativeStreams.isNotEmpty()) {
            add("${channel.alternativeStreams.size} alternate stream path(s) available.")
        }
        if (channel.catchUpSupported && channel.streamId <= 0L && channel.catchUpSource.isNullOrBlank()) {
            add("Replay may fail because this provider did not expose a catch-up template.")
        }
    }
    return currentState.copy(
        alternativeStreamCount = channel.alternativeStreams.size,
        channelErrorCount = channel.errorCount,
        archiveSupportLabel = archiveLabel,
        troubleshootingHints = hints.take(4)
    )
}