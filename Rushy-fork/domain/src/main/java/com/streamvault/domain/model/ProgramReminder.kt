package com.streamvault.domain.model

data class ProgramReminder(
    val id: Long = 0,
    val providerId: Long,
    val channelId: String,
    val channelName: String,
    val programTitle: String,
    val programStartTime: Long,
    val remindAt: Long,
    val leadTimeMinutes: Int = 5,
    val isDismissed: Boolean = false,
    val notifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
