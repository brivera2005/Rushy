package com.streamvault.data.manager

import com.streamvault.data.local.dao.ProgramReminderDao
import com.streamvault.data.local.entity.ProgramReminderEntity
import com.streamvault.data.manager.reminder.ProgramReminderAlarmScheduler
import com.streamvault.data.manager.reminder.ProgramReminderNotifier
import com.streamvault.domain.manager.ProgramReminderManager
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.ProgramReminder
import com.streamvault.domain.model.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private const val UPCOMING_REMINDER_REFRESH_MS = 15_000L

@Singleton
class ProgramReminderManagerImpl private constructor(
    private val programReminderDao: ProgramReminderDao,
    private val alarmScheduler: ProgramReminderAlarmScheduler,
    private val notifier: ProgramReminderNotifier,
    private val nowProvider: () -> Long,
    private val upcomingTimeFlow: Flow<Long>
) : ProgramReminderManager {

    @Inject
    constructor(
        programReminderDao: ProgramReminderDao,
        alarmScheduler: ProgramReminderAlarmScheduler,
        notifier: ProgramReminderNotifier
    ) : this(
        programReminderDao = programReminderDao,
        alarmScheduler = alarmScheduler,
        notifier = notifier,
        nowProvider = System::currentTimeMillis,
        upcomingTimeFlow = upcomingReminderTimeFlow()
    )

    internal companion object {
        const val REMINDER_STALE_GRACE_MS = 2 * 60_000L

        fun forTesting(
            programReminderDao: ProgramReminderDao,
            alarmScheduler: ProgramReminderAlarmScheduler,
            notifier: ProgramReminderNotifier,
            nowProvider: () -> Long,
            upcomingTimeFlow: Flow<Long>
        ): ProgramReminderManagerImpl = ProgramReminderManagerImpl(
            programReminderDao = programReminderDao,
            alarmScheduler = alarmScheduler,
            notifier = notifier,
            nowProvider = nowProvider,
            upcomingTimeFlow = upcomingTimeFlow
        )
    }

    override fun observeUpcomingReminders(): Flow<List<ProgramReminder>> =
        programReminderDao.observeUpcoming()
            .combine(upcomingTimeFlow) { reminders, now ->
                reminders
                    .asSequence()
                    .filter { !it.isDismissed && it.programStartTime >= now }
                    .map { it.asDomain() }
                    .toList()
            }

    override suspend fun isReminderScheduled(
        providerId: Long,
        channelId: String,
        programTitle: String,
        programStartTime: Long
    ): Boolean {
        val reminder = programReminderDao.getByProgram(providerId, channelId, programTitle, programStartTime)
        return reminder != null && !reminder.isDismissed
    }

    override suspend fun scheduleReminder(
        providerId: Long,
        channelId: String,
        channelName: String,
        program: Program,
        leadTimeMinutes: Int
    ): Result<Unit> {
        if (providerId <= 0L) return Result.error("Program reminders need a synced provider.")
        if (program.startTime <= System.currentTimeMillis()) {
            return Result.error("This program has already started.")
        }
        if (!alarmScheduler.canScheduleExactAlarms()) {
            return Result.error(ProgramReminderAlarmScheduler.EXACT_ALARM_PERMISSION_MESSAGE)
        }

        val now = System.currentTimeMillis()
        val remindAt = (program.startTime - leadTimeMinutes * 60_000L).coerceAtLeast(now + 1_000L)
        val existing = programReminderDao.getByProgram(providerId, channelId, program.title, program.startTime)
        val reminder = ProgramReminderEntity(
            id = existing?.id ?: 0L,
            providerId = providerId,
            channelId = channelId,
            channelName = channelName,
            programTitle = program.title,
            programStartTime = program.startTime,
            remindAt = remindAt,
            leadTimeMinutes = leadTimeMinutes,
            isDismissed = false,
            notifiedAt = null,
            createdAt = existing?.createdAt ?: now
        )
        val reminderId = if (existing == null) {
            programReminderDao.insert(reminder)
        } else {
            programReminderDao.update(reminder)
            existing.id
        }
        return when (val result = alarmScheduler.schedule(reminderId, remindAt)) {
            is Result.Success -> Result.success(Unit)
            is Result.Error -> {
                if (existing == null) {
                    programReminderDao.deleteById(reminderId)
                }
                Result.error(result.message, result.exception)
            }
            Result.Loading -> Result.error("Unexpected reminder scheduling state")
        }
    }

    override suspend fun cancelReminder(
        providerId: Long,
        channelId: String,
        programTitle: String,
        programStartTime: Long
    ): Result<Unit> {
        val existing = programReminderDao.getByProgram(providerId, channelId, programTitle, programStartTime)
            ?: return Result.success(Unit)
        programReminderDao.deleteById(existing.id)
        alarmScheduler.cancel(existing.id)
        return Result.success(Unit)
    }

    override suspend fun restoreScheduledReminders() {
        val now = System.currentTimeMillis()
        programReminderDao.getPendingActive(now).forEach { reminder ->
            when (val result = alarmScheduler.schedule(reminder.id, reminder.remindAt.coerceAtLeast(now + 1_000L))) {
                is Result.Error -> android.util.Log.w("ProgramReminderManager", "Unable to restore reminder ${reminder.id}: ${result.message}")
                else -> Unit
            }
        }
    }

    suspend fun deliverReminder(reminderId: Long) {
        val reminder = programReminderDao.getById(reminderId) ?: return
        if (reminder.isDismissed || reminder.notifiedAt != null) return
        val now = System.currentTimeMillis()
        if (now - reminder.programStartTime > REMINDER_STALE_GRACE_MS) {
            programReminderDao.update(reminder.copy(isDismissed = true))
            return
        }
        notifier.showReminder(reminder)
        programReminderDao.update(reminder.copy(notifiedAt = now))
    }

    private fun ProgramReminderEntity.asDomain(): ProgramReminder = ProgramReminder(
        id = id,
        providerId = providerId,
        channelId = channelId,
        channelName = channelName,
        programTitle = programTitle,
        programStartTime = programStartTime,
        remindAt = remindAt,
        leadTimeMinutes = leadTimeMinutes,
        isDismissed = isDismissed,
        notifiedAt = notifiedAt,
        createdAt = createdAt
    )
}

private fun upcomingReminderTimeFlow(refreshMs: Long = UPCOMING_REMINDER_REFRESH_MS): Flow<Long> = flow {
    emit(System.currentTimeMillis())
    while (true) {
        delay(refreshMs)
        emit(System.currentTimeMillis())
    }
}
