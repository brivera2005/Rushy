package com.streamvault.app.ui.screens.player

import androidx.lifecycle.viewModelScope
import com.streamvault.app.ui.model.isArchivePlayable
import com.streamvault.data.security.CredentialDecryptionException
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.StreamInfo
import kotlinx.coroutines.launch

internal suspend fun resolveCatchUpStreamInfo(
    candidateUrl: String,
    title: String,
    currentContentId: Long,
    currentProviderId: Long,
    resolveStreamInfo: suspend (String, Long, Long, ContentType) -> StreamInfo?
): StreamInfo? = resolveStreamInfo(candidateUrl, currentContentId, currentProviderId, ContentType.LIVE)
    ?.copy(title = title)

internal suspend fun PlayerViewModel.startCatchUpPlayback(
    urls: List<String>,
    title: String,
    recoveryAction: String,
    requestVersionOverride: Long? = null
) {
    val requestVersion = requestVersionOverride ?: beginPlaybackSession()
    val candidates = urls
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    val primaryUrl = candidates.firstOrNull() ?: return

    currentTitle = title
    pendingCatchUpUrls = candidates
    triedAlternativeStreams.clear()
    triedAlternativeStreams.add(primaryUrl)
    currentStreamUrl = primaryUrl
    updateStreamClass("Catch-up")
    appendRecoveryAction(recoveryAction)

    val catchupStream = resolveCatchUpStreamInfo(
        candidateUrl = primaryUrl,
        title = currentTitle,
        currentContentId = currentContentId,
        currentProviderId = currentProviderId,
        resolveStreamInfo = ::resolvePlaybackStreamInfo
    ) ?: return
    if (!preparePlayer(catchupStream, requestVersion)) return
    playerEngine.play()
}

fun PlayerViewModel.playCatchUp(program: Program) {
    viewModelScope.launch {
        val requestVersion = prepareRequestVersion
        val channel = currentChannelFlow.value
        if (channel == null || !channel.isArchivePlayable(program)) {
            return@launch
        }
        val start = program.startTime / 1000L
        val end = program.endTime / 1000L
        val streamId = channel.id
        val providerId = currentProviderId

        if (providerId == -1L || streamId == 0L) {
            setLastFailureReason("Catch-up playback needs a valid live channel context.")
            showPlayerNotice(
                message = "Catch-up playback needs a valid live channel context.",
                recoveryType = PlayerRecoveryType.CATCH_UP,
                actions = buildRecoveryActions(PlayerRecoveryType.CATCH_UP)
            )
            return@launch
        }

        val catchUpUrls = try {
            providerRepository.buildCatchUpUrls(providerId, streamId, start, end)
        } catch (e: CredentialDecryptionException) {
            if (!isActivePlaybackSession(requestVersion)) return@launch
            setLastFailureReason(e.message ?: CredentialDecryptionException.MESSAGE)
            showPlayerNotice(
                message = e.message ?: CredentialDecryptionException.MESSAGE,
                recoveryType = PlayerRecoveryType.SOURCE,
                actions = buildRecoveryActions(PlayerRecoveryType.SOURCE)
            )
            return@launch
        }
        if (!isActivePlaybackSession(requestVersion)) return@launch
        if (catchUpUrls.isNotEmpty()) {
            startCatchUpPlayback(
                urls = catchUpUrls,
                title = "${channel.name}: ${program.title}",
                recoveryAction = "Started program replay"
            )
        } else {
            val reason = resolveCatchUpFailureMessage(
                channel,
                archiveRequested = true,
                programHasArchive = program.hasArchive
            )
            setLastFailureReason(reason)
            showPlayerNotice(
                message = reason,
                recoveryType = PlayerRecoveryType.CATCH_UP,
                actions = buildRecoveryActions(PlayerRecoveryType.CATCH_UP)
            )
        }
    }
}