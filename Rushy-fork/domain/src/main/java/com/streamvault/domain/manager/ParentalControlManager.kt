package com.streamvault.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalControlManager @Inject constructor(
    private val sessionStore: ParentalControlSessionStore
) {
    private val _unlockedCategoriesByProvider = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val unlockedCategoriesByProvider: StateFlow<Map<Long, Set<Long>>> =
        _unlockedCategoriesByProvider.asStateFlow()

    init {
        if (sessionStore.readSessionState().unlockedCategoryIdsByProvider.isNotEmpty()) {
            sessionStore.writeSessionState(ParentalControlSessionState())
        }
    }

    fun unlockedCategoriesForProvider(providerId: Long) =
        unlockedCategoriesByProvider.map { it[providerId] ?: emptySet() }

    fun unlockCategory(providerId: Long, categoryId: Long) {
        _unlockedCategoriesByProvider.update { current ->
            (current + (providerId to setOf(categoryId)))
                .filterValues { it.isNotEmpty() }
        }
        sessionStore.writeSessionState(ParentalControlSessionState())
    }

    // StateFlow.value reads are thread-safe without an external lock.
    fun isCategoryUnlocked(providerId: Long, categoryId: Long): Boolean =
        _unlockedCategoriesByProvider.value[providerId]?.contains(categoryId) == true

    fun retainUnlockedCategory(providerId: Long, categoryId: Long?) {
        _unlockedCategoriesByProvider.update { current ->
            val retainedCategoryIds = categoryId
                ?.takeIf { id -> current[providerId]?.contains(id) == true }
                ?.let(::setOf)
                .orEmpty()
            if (retainedCategoryIds.isEmpty()) {
                current - providerId
            } else {
                (current + (providerId to retainedCategoryIds))
                    .filterValues { it.isNotEmpty() }
            }
        }
        sessionStore.writeSessionState(ParentalControlSessionState())
    }

    fun clearUnlockedCategories(providerId: Long? = null) {
        _unlockedCategoriesByProvider.update { current ->
            if (providerId == null) emptyMap() else current - providerId
        }
        sessionStore.writeSessionState(ParentalControlSessionState())
    }
}
