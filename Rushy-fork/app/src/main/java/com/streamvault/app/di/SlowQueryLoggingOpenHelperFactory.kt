package com.streamvault.app.di

import android.database.Cursor
import android.os.SystemClock
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement

internal class SlowQueryLoggingOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val slowQueryThresholdMs: Long,
    private val tag: String = "RoomSlowQuery"
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return SlowQueryLoggingOpenHelper(
            delegate = delegate.create(configuration),
            slowQueryThresholdMs = slowQueryThresholdMs,
            tag = tag
        )
    }
}

private class SlowQueryLoggingOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val slowQueryThresholdMs: Long,
    private val tag: String
) : SupportSQLiteOpenHelper by delegate {

    override val writableDatabase: SupportSQLiteDatabase
        get() = SlowQueryLoggingDatabase(delegate.writableDatabase, slowQueryThresholdMs, tag)

    override val readableDatabase: SupportSQLiteDatabase
        get() = SlowQueryLoggingDatabase(delegate.readableDatabase, slowQueryThresholdMs, tag)
}

private class SlowQueryLoggingDatabase(
    private val delegate: SupportSQLiteDatabase,
    private val slowQueryThresholdMs: Long,
    private val tag: String
) : SupportSQLiteDatabase by delegate {

    override fun compileStatement(sql: String): SupportSQLiteStatement {
        return SlowQueryLoggingStatement(
            delegate = delegate.compileStatement(sql),
            sql = sql,
            slowQueryThresholdMs = slowQueryThresholdMs,
            tag = tag
        )
    }

    override fun query(query: String): Cursor =
        logSlowSql(query) { delegate.query(query) }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor =
        logSlowSql(query) { delegate.query(query, bindArgs) }

    override fun query(query: SupportSQLiteQuery): Cursor =
        logSlowSql(query.sql) { delegate.query(query) }

    override fun query(query: SupportSQLiteQuery, cancellationSignal: android.os.CancellationSignal?): Cursor =
        logSlowSql(query.sql) { delegate.query(query, cancellationSignal) }

    override fun execSQL(sql: String) {
        logSlowSql(sql) { delegate.execSQL(sql) }
    }

    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        logSlowSql(sql) { delegate.execSQL(sql, bindArgs) }
    }

    private inline fun <T> logSlowSql(sql: String, block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
            if (elapsedMs >= slowQueryThresholdMs) {
                Log.w(tag, "Slow SQL (${elapsedMs}ms): ${sql.normalizeForLog()}")
            }
        }
    }
}

private class SlowQueryLoggingStatement(
    private val delegate: SupportSQLiteStatement,
    private val sql: String,
    private val slowQueryThresholdMs: Long,
    private val tag: String
) : SupportSQLiteStatement by delegate {

    override fun execute() {
        logSlowStatement { delegate.execute() }
    }

    override fun executeUpdateDelete(): Int =
        logSlowStatement { delegate.executeUpdateDelete() }

    override fun executeInsert(): Long =
        logSlowStatement { delegate.executeInsert() }

    override fun simpleQueryForLong(): Long =
        logSlowStatement { delegate.simpleQueryForLong() }

    override fun simpleQueryForString(): String? =
        logSlowStatement { delegate.simpleQueryForString() }

    private inline fun <T> logSlowStatement(block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
            if (elapsedMs >= slowQueryThresholdMs) {
                Log.w(tag, "Slow SQL (${elapsedMs}ms): ${sql.normalizeForLog()}")
            }
        }
    }
}

private fun String.normalizeForLog(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .let { normalized ->
            if (normalized.length <= 240) normalized else normalized.take(237) + "..."
        }
}
