package com.streamvault.domain.manager

data class ParentalControlSessionState(
    val unlockedCategoryIdsByProvider: Map<Long, Set<Long>> = emptyMap()
)

interface ParentalControlSessionStore {
    fun readSessionState(): ParentalControlSessionState
    fun writeSessionState(state: ParentalControlSessionState)
}