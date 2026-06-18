package com.streamvault.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSupportSnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val reportFile: File = File(context.filesDir, "diagnostics/crash/latest-playback-support.txt").also {
        it.parentFile?.mkdirs()
    }

    fun write(report: String) {
        runCatching {
            reportFile.writeText(report, Charsets.UTF_8)
        }
    }
}
