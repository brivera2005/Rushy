package com.streamvault.player.timeshift

import android.content.Context
import java.io.File

/**
 * Manages the timeshift cache directory:
 *
 * - Enforces a global byte budget across all session data.
 * - Deletes stale session directories left by crashes or force-stops.
 * - Evicts the oldest (LRU) session directories first when over-budget.
 *
 * All methods are safe to call from IO threads.
 */
class TimeshiftDiskManager(
    context: Context,
    val maxBudgetBytes: Long = DEFAULT_BUDGET_BYTES
) {
    private val timeshiftDir = File(context.cacheDir, "timeshift")

    /**
     * Deletes every directory under [timeshiftDir] except [activeSessionDir].
     * Safe to call with [activeSessionDir] = null to wipe everything (e.g. on app start
     * before any session exists).
     */
    fun cleanupStaleDirectories(activeSessionDir: File?) {
        val entries = timeshiftDir.listFiles() ?: return
        for (entry in entries) {
            if (entry == activeSessionDir) continue
            entry.deleteRecursively()
        }
    }

    /**
     * Returns the total bytes consumed by all files under [timeshiftDir].
     */
    fun currentUsageBytes(): Long =
        timeshiftDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Returns true when the total usage is below [maxBudgetBytes].
     */
    fun isWithinBudget(): Boolean = currentUsageBytes() < maxBudgetBytes

    /**
     * Deletes stale session directories in oldest-first (LRU) order until usage
     * falls below 80 % of [maxBudgetBytes], or no more stale dirs remain.
     *
     * [activeSessionDir] is never touched.
     */
    fun evictLruUntilWithinBudget(activeSessionDir: File?) {
        val staleDirs = timeshiftDir.listFiles()
            ?.filter { it.isDirectory && it != activeSessionDir }
            ?.sortedBy { it.lastModified() }
            ?: return
        val target = (maxBudgetBytes * 0.8).toLong()
        for (dir in staleDirs) {
            if (currentUsageBytes() < target) break
            dir.deleteRecursively()
        }
    }

    companion object {
        /** Default 2 GB global budget for all timeshift data. */
        const val DEFAULT_BUDGET_BYTES = 2L * 1024 * 1024 * 1024
    }
}
