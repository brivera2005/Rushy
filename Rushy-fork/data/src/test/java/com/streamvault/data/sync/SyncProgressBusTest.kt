package com.streamvault.data.sync

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.sync.Section
import com.streamvault.domain.sync.SyncProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests unitaires de [SyncProgressBus].
 *
 * Couvre les 4 cas du test plan §7 du SCOPE M1 :
 * 1. `emit` met à jour la valeur courante du flow.
 * 2. `reset` remet le flow à `null`.
 * 3. Deux collectors observent la même séquence d'émissions.
 * 4. Un collector qui souscrit tardivement reçoit la dernière valeur (contrat StateFlow).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncProgressBusTest {

    @Test
    fun emit_updatesFlowValue() = runTest {
        val bus = SyncProgressBus()
        val progress = SyncProgress(
            section = Section.LIVE,
            current = 3,
            total = 10,
            currentLabel = "Sport",
            itemsIndexed = 42
        )

        bus.emit(progress)

        assertThat(bus.flow.value).isEqualTo(progress)
    }

    @Test
    fun reset_setsFlowToNull() = runTest {
        val bus = SyncProgressBus()
        bus.emit(
            SyncProgress(
                section = Section.VOD,
                current = 1,
                total = 5,
                currentLabel = "Movies",
                itemsIndexed = 100
            )
        )
        assertThat(bus.flow.value).isNotNull()

        bus.reset()

        assertThat(bus.flow.value).isNull()
    }

    @Test
    fun twoCollectors_receiveSameSequence() = runTest(UnconfinedTestDispatcher()) {
        val bus = SyncProgressBus()
        val first = mutableListOf<SyncProgress?>()
        val second = mutableListOf<SyncProgress?>()

        val firstJob = launch { bus.flow.toList(first) }
        val secondJob = launch { bus.flow.toList(second) }

        val progressLive = SyncProgress(Section.LIVE, 1, 4, "Live", 10)
        val progressVod = SyncProgress(Section.VOD, 2, 4, "VOD", 20)
        bus.emit(progressLive)
        bus.emit(progressVod)
        bus.reset()

        firstJob.cancel()
        secondJob.cancel()

        assertThat(first).containsExactly(null, progressLive, progressVod, null).inOrder()
        assertThat(second).containsExactly(null, progressLive, progressVod, null).inOrder()
    }

    @Test
    fun lateCollector_receivesReplayValue() = runTest(UnconfinedTestDispatcher()) {
        val bus = SyncProgressBus()
        val progress = SyncProgress(
            section = Section.SERIES,
            current = 7,
            total = 12,
            currentLabel = "Drama",
            itemsIndexed = 350
        )

        bus.emit(progress)

        val received = mutableListOf<SyncProgress?>()
        val job = launch { bus.flow.toList(received) }
        job.cancel()

        assertThat(received).containsExactly(progress)
    }
}
