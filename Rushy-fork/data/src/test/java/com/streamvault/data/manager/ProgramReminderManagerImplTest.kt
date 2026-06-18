package com.streamvault.data.manager

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.ProgramReminderDao
import com.streamvault.data.local.entity.ProgramReminderEntity
import com.streamvault.data.manager.reminder.ProgramReminderAlarmScheduler
import com.streamvault.data.manager.reminder.ProgramReminderNotifier
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.ProgramReminder
import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProgramReminderManagerImplTest {

    private val dao: ProgramReminderDao = mock()
    private val alarmScheduler: ProgramReminderAlarmScheduler = mock()
    private val notifier: ProgramReminderNotifier = mock()

    private val manager = ProgramReminderManagerImpl(
        programReminderDao = dao,
        alarmScheduler = alarmScheduler,
        notifier = notifier
    )

    init {
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(true)
        whenever(alarmScheduler.schedule(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(Result.success(Unit))
    }

    @Test
    fun `scheduleReminder inserts reminder and schedules alarm`() = runTest {
        val now = System.currentTimeMillis()
        val program = Program(
            channelId = "bbc1",
            title = "World News",
            startTime = now + 30 * 60_000L,
            endTime = now + 60 * 60_000L,
            providerId = 7L
        )
        whenever(dao.getByProgram(7L, "bbc1", "World News", program.startTime)).thenReturn(null)
        whenever(dao.insert(org.mockito.kotlin.any())).thenReturn(42L)

        val result = manager.scheduleReminder(
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            program = program
        )

        assertThat(result).isInstanceOf(com.streamvault.domain.model.Result.Success::class.java)
        verify(dao).insert(org.mockito.kotlin.any())
        verify(alarmScheduler).schedule(eq(42L), org.mockito.kotlin.any())
    }

    @Test
    fun `scheduleReminder fails when exact alarms are unavailable`() = runTest {
        val now = System.currentTimeMillis()
        val program = Program(
            channelId = "bbc1",
            title = "World News",
            startTime = now + 30 * 60_000L,
            endTime = now + 60 * 60_000L,
            providerId = 7L
        )
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(false)

        val result = manager.scheduleReminder(
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            program = program
        )

        assertThat(result).isInstanceOf(Result.Error::class.java)
        verify(dao, never()).insert(org.mockito.kotlin.any())
        verify(alarmScheduler, never()).schedule(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `cancelReminder deletes reminder and cancels alarm`() = runTest {
        val reminder = ProgramReminderEntity(
            id = 42L,
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            programTitle = "World News",
            programStartTime = 1_000L,
            remindAt = 900L
        )
        whenever(dao.getByProgram(7L, "bbc1", "World News", 1_000L)).thenReturn(reminder)

        manager.cancelReminder(
            providerId = 7L,
            channelId = "bbc1",
            programTitle = "World News",
            programStartTime = 1_000L
        )

        verify(dao).deleteById(42L)
        verify(alarmScheduler).cancel(42L)
    }

    @Test
    fun `restoreScheduledReminders coerces overdue reminder alarms into the future`() = runTest {
        val nowBeforeRestore = System.currentTimeMillis()
        val reminder = ProgramReminderEntity(
            id = 42L,
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            programTitle = "World News",
            programStartTime = nowBeforeRestore + 5 * 60_000L,
            remindAt = nowBeforeRestore - 60_000L
        )
        whenever(dao.getPendingActive(org.mockito.kotlin.any())).thenReturn(listOf(reminder))

        manager.restoreScheduledReminders()

        val remindAtCaptor = argumentCaptor<Long>()
        verify(alarmScheduler).schedule(eq(42L), remindAtCaptor.capture())
        assertThat(remindAtCaptor.firstValue).isAtLeast(nowBeforeRestore + 1_000L)
    }

    @Test
    fun `restoreScheduledReminders continues after one reminder fails to reschedule`() = runTest {
        val now = System.currentTimeMillis()
        val first = ProgramReminderEntity(
            id = 41L,
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            programTitle = "World News",
            programStartTime = now + 5 * 60_000L,
            remindAt = now + 60_000L
        )
        val second = ProgramReminderEntity(
            id = 42L,
            providerId = 7L,
            channelId = "bbc2",
            channelName = "BBC Two",
            programTitle = "Documentary",
            programStartTime = now + 10 * 60_000L,
            remindAt = now + 120_000L
        )
        whenever(dao.getPendingActive(org.mockito.kotlin.any())).thenReturn(listOf(first, second))
        whenever(alarmScheduler.schedule(41L, first.remindAt)).thenReturn(Result.error("denied"))

        manager.restoreScheduledReminders()

        verify(alarmScheduler).schedule(41L, first.remindAt)
        verify(alarmScheduler).schedule(42L, second.remindAt)
    }

    @Test
    fun `deliverReminder notifies once and marks reminder notified`() = runTest {
        val reminder = ProgramReminderEntity(
            id = 42L,
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            programTitle = "World News",
            programStartTime = System.currentTimeMillis() + 5 * 60_000L,
            remindAt = System.currentTimeMillis()
        )
        whenever(dao.getById(42L)).thenReturn(reminder)

        manager.deliverReminder(42L)

        verify(notifier).showReminder(reminder)
        val updatedCaptor = argumentCaptor<ProgramReminderEntity>()
        verify(dao).update(updatedCaptor.capture())
        assertThat(updatedCaptor.firstValue.notifiedAt).isNotNull()
    }

    @Test
    fun `deliverReminder skips stale reminders and dismisses them`() = runTest {
        val reminder = ProgramReminderEntity(
            id = 42L,
            providerId = 7L,
            channelId = "bbc1",
            channelName = "BBC One",
            programTitle = "World News",
            programStartTime = System.currentTimeMillis() - 3 * 60_000L,
            remindAt = System.currentTimeMillis() - 8 * 60_000L
        )
        whenever(dao.getById(42L)).thenReturn(reminder)

        manager.deliverReminder(42L)

        verify(notifier, never()).showReminder(org.mockito.kotlin.any())
        val updatedCaptor = argumentCaptor<ProgramReminderEntity>()
        verify(dao).update(updatedCaptor.capture())
        assertThat(updatedCaptor.firstValue.isDismissed).isTrue()
        assertThat(updatedCaptor.firstValue.notifiedAt).isNull()
    }

    @Test
    fun `observeUpcomingReminders drops expired reminders when only time advances`() = runTest {
        var now = 10_000L
        val reminders = MutableStateFlow(
            listOf(
                ProgramReminderEntity(
                    id = 42L,
                    providerId = 7L,
                    channelId = "bbc1",
                    channelName = "BBC One",
                    programTitle = "World News",
                    programStartTime = now + 1_000L,
                    remindAt = now - 30_000L
                )
            )
        )
        val upcomingTimeFlow = MutableStateFlow(now)
        val flowDao = object : ProgramReminderDao {
            override fun observeUpcoming(): Flow<List<ProgramReminderEntity>> = reminders

            override suspend fun getIdsByProvider(providerId: Long): List<Long> = error("unused")

            override suspend fun getByProgram(
                providerId: Long,
                channelId: String,
                programTitle: String,
                programStartTime: Long
            ): ProgramReminderEntity? = error("unused")

            override suspend fun getById(id: Long): ProgramReminderEntity? = error("unused")

            override suspend fun getPendingActive(now: Long): List<ProgramReminderEntity> = error("unused")

            override suspend fun insert(reminder: ProgramReminderEntity): Long = error("unused")

            override suspend fun update(reminder: ProgramReminderEntity) = error("unused")

            override suspend fun deleteByProgram(
                providerId: Long,
                channelId: String,
                programTitle: String,
                programStartTime: Long
            ) = error("unused")

            override suspend fun deleteById(id: Long) = error("unused")

            override suspend fun deleteExpired(beforeTime: Long): Int = error("unused")
        }
        val timedManager = ProgramReminderManagerImpl.forTesting(
            programReminderDao = flowDao,
            alarmScheduler = alarmScheduler,
            notifier = notifier,
            nowProvider = { now },
            upcomingTimeFlow = upcomingTimeFlow
        )
        val initial = timedManager.observeUpcomingReminders().first()
        assertThat(initial.map(ProgramReminder::id)).containsExactly(42L)

        now += 2_000L
        upcomingTimeFlow.value = now

        val expired = timedManager.observeUpcomingReminders().first()
        assertThat(expired).isEmpty()
    }
}
