package com.streamvault.data.manager.recording

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingForegroundIdleGateTest {

    @Test
    fun `pending command keeps service alive when active count is zero`() {
        val gate = RecordingForegroundIdleGate()

        gate.onCommandStarted()
        gate.onActiveCountChanged(0)

        assertThat(gate.shouldKeepAlive()).isTrue()
        assertThat(gate.hasPendingCommands).isTrue()
    }

    @Test
    fun `service becomes idle only after pending command completes and no recordings remain`() {
        val gate = RecordingForegroundIdleGate()

        gate.onCommandStarted()
        gate.onActiveCountChanged(0)
        gate.onCommandFinished()

        assertThat(gate.shouldKeepAlive()).isFalse()
        assertThat(gate.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun `active recording keeps service alive after pending command finishes`() {
        val gate = RecordingForegroundIdleGate()

        gate.onCommandStarted()
        gate.onActiveCountChanged(1)
        gate.onCommandFinished()

        assertThat(gate.shouldKeepAlive()).isTrue()
        assertThat(gate.activeRecordingCount).isEqualTo(1)
    }
}