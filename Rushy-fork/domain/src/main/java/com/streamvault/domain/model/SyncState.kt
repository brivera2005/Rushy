package com.streamvault.domain.model

/**
 * Represents every stage of a provider sync operation.
 *
 * Designed as a sealed hierarchy so `when` branches are exhaustive and
 * the compiler enforces handling of every state.
 */
sealed class SyncState {

    /** No sync is in progress and no result is available yet. */
    object Idle : SyncState()

    /** A sync is actively running. [phase] describes the current work (e.g. "Downloading Live TV…"). */
    data class Syncing(val phase: String = "") : SyncState()

    /** The most recent sync completed successfully at [timestamp] (epoch millis). */
    data class Success(val timestamp: Long = System.currentTimeMillis()) : SyncState()

    /**
     * The most recent sync completed but with non-fatal degradations.
     *
     * @param message               Human-readable partial-sync summary.
     * @param warnings              Detailed list of failed/skipped sub-operations.
     * @param hasRetryableEpgFailure True when the EPG portion failed due to a transient
     *                               network/IO condition that WorkManager backoff should
     *                               retry. Callers that only care about warnings may ignore
     *                               this flag.
     */
    data class Partial(
        val message: String,
        val warnings: List<String> = emptyList(),
        val hasRetryableEpgFailure: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncState()

    /**
     * The most recent sync failed.
     *
     * @param message Human-readable description of the failure.
     * @param cause   Optional underlying exception for diagnostics.
     */
    data class Error(val message: String, val cause: Throwable? = null) : SyncState()

    // ── Convenience helpers ─────────────────────────────────────────

    val isSyncing: Boolean get() = this is Syncing
    val isError: Boolean get() = this is Error
    val isSuccess: Boolean get() = this is Success
    val isPartial: Boolean get() = this is Partial
}
