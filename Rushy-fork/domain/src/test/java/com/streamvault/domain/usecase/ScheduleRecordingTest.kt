package com.streamvault.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.manager.RecordingManager
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.RecordingItem
import com.streamvault.domain.model.RecordingRecurrence
import com.streamvault.domain.model.RecordingRequest
import com.streamvault.domain.model.RecordingStorageState
import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScheduleRecordingTest {

    @Test
    fun rejects_non_live_or_missing_context() = runTest {
        val manager = FakeRecordingManager()
        val useCase = ScheduleRecording(manager)

        val result = useCase(
            ScheduleRecordingCommand(
                contentType = ContentType.MOVIE,
                providerId = 1L,
                channel = null,
                streamUrl = "https://example.com/live.ts",
                currentProgram = null,
                nextProgram = null,
                recurrence = RecordingRecurrence.NONE,
                nowMs = 1_000L
            )
        )

        assertThat((result as Result.Error).message).isEqualTo("Recording needs guide timing for the current live channel.")
        assertThat(manager.lastScheduledRequest).isNull()
    }

    @Test
    fun prefers_next_program_when_available() = runTest {
        val manager = FakeRecordingManager()
        val useCase = ScheduleRecording(manager)

        useCase(
            ScheduleRecordingCommand(
                contentType = ContentType.LIVE,
                providerId = 7L,
                channel = channel(),
                streamUrl = "https://example.com/live.ts",
                currentProgram = program(title = "Current", startTime = 1_000L, endTime = 2_000L),
                nextProgram = program(title = "Next", startTime = 3_000L, endTime = 4_000L),
                recurrence = RecordingRecurrence.DAILY,
                nowMs = 1_500L
            )
        )

        assertThat(manager.lastScheduledRequest?.programTitle).isEqualTo("Next")
        assertThat(manager.lastScheduledRequest?.scheduledStartMs).isEqualTo(3_000L)
        assertThat(manager.lastScheduledRequest?.recurrence).isEqualTo(RecordingRecurrence.DAILY)
    }

    @Test
    fun clamps_current_program_start_to_now_when_needed() = runTest {
        val manager = FakeRecordingManager()
        val useCase = ScheduleRecording(manager)

        useCase(
            ScheduleRecordingCommand(
                contentType = ContentType.LIVE,
                providerId = 7L,
                channel = channel(),
                streamUrl = "https://example.com/live.ts",
                currentProgram = program(title = "Current", startTime = 1_000L, endTime = 4_000L),
                nextProgram = null,
                recurrence = RecordingRecurrence.NONE,
                nowMs = 2_500L
            )
        )

        assertThat(manager.lastScheduledRequest?.programTitle).isEqualTo("Current")
        assertThat(manager.lastScheduledRequest?.scheduledStartMs).isEqualTo(2_500L)
        assertThat(manager.lastScheduledRequest?.scheduledEndMs).isEqualTo(4_000L)
    }

    @Test
    fun returns_error_when_program_has_already_ended() = runTest {
        val manager = FakeRecordingManager()
        val useCase = ScheduleRecording(manager)

        val result = useCase(
            ScheduleRecordingCommand(
                contentType = ContentType.LIVE,
                providerId = 7L,
                channel = channel(),
                streamUrl = "https://example.com/live.ts",
                currentProgram = program(title = "Ended", startTime = 1_000L, endTime = 2_000L),
                nextProgram = null,
                recurrence = RecordingRecurrence.NONE,
                nowMs = 2_000L
            )
        )

        assertThat((result as Result.Error).message)
            .isEqualTo("The selected program has already ended. Refresh the guide and try again.")
        assertThat(manager.lastScheduledRequest).isNull()
    }

    private fun channel() = Channel(
        id = 11L,
        name = "Channel 11",
        streamUrl = "https://example.com/live.ts",
        providerId = 7L
    )

    private fun program(title: String, startTime: Long, endTime: Long) = Program(
        id = 1L,
        channelId = "11",
        title = title,
        startTime = startTime,
        endTime = endTime,
        providerId = 7L
    )

    private class FakeRecordingManager : RecordingManager {
        var lastScheduledRequest: RecordingRequest? = null

        override fun observeRecordingItems(): Flow<List<RecordingItem>> = flowOf(emptyList())
        override fun observeStorageState(): Flow<RecordingStorageState> = flowOf(RecordingStorageState())
        override suspend fun startManualRecording(request: RecordingRequest): Result<RecordingItem> = error("Not used in test")
        override suspend fun scheduleRecording(request: RecordingRequest): Result<RecordingItem> {
            lastScheduledRequest = request
            return Result.success(
                RecordingItem(
                    id = "scheduled-1",
                    providerId = request.providerId,
                    channelId = request.channelId,
                    channelName = request.channelName,
                    streamUrl = request.streamUrl,
                    scheduledStartMs = request.scheduledStartMs,
                    scheduledEndMs = request.scheduledEndMs,
                    programTitle = request.programTitle,
                    recurrence = request.recurrence
                )
            )
        }
        override suspend fun stopRecording(recordingId: String): Result<Unit> = error("Not used in test")
        override suspend fun cancelRecording(recordingId: String): Result<Unit> = error("Not used in test")
        override suspend fun deleteRecording(recordingId: String): Result<Unit> = error("Not used in test")
    }
}