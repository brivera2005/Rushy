package com.streamvault.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

interface DatabaseTransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

@Singleton
class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: StreamVaultDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }
}
