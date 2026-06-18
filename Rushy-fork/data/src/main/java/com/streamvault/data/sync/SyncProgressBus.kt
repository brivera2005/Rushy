package com.streamvault.data.sync

import com.streamvault.domain.sync.SyncProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus singleton de progression du sync catalogue.
 *
 * Publie le dernier [SyncProgress] émis par `SyncManager` et ses stratégies, consommé
 * par `WelcomeViewModel` côté `:app`. Pattern miroir de `SyncStateTracker` (StateFlow
 * privé mutable + façade publique read-only).
 *
 * Valeur initiale `null` = aucun sync en cours / écran fallback. `reset()` ramène
 * explicitement le flow à `null` à la fin de chaque cycle (succès, échec ou abort
 * low-memory) pour éviter qu'un écran ultérieur n'hérite d'un état obsolète.
 */
@Singleton
class SyncProgressBus @Inject constructor() {

    private val _flow = MutableStateFlow<SyncProgress?>(null)

    val flow: StateFlow<SyncProgress?> = _flow.asStateFlow()

    fun emit(progress: SyncProgress) {
        _flow.value = progress
    }

    fun reset() {
        _flow.value = null
    }
}
