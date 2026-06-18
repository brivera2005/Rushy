package com.streamvault.data.manager.recording

internal class RecordingForegroundIdleGate {
    var activeRecordingCount: Int = 0
        private set

    var pendingCommandCount: Int = 0
        private set

    val hasPendingCommands: Boolean
        get() = pendingCommandCount > 0

    fun onCommandStarted() {
        pendingCommandCount += 1
    }

    fun onCommandFinished() {
        pendingCommandCount = (pendingCommandCount - 1).coerceAtLeast(0)
    }

    fun onActiveCountChanged(count: Int) {
        activeRecordingCount = count.coerceAtLeast(0)
    }

    fun shouldKeepAlive(): Boolean = hasPendingCommands || activeRecordingCount > 0
}