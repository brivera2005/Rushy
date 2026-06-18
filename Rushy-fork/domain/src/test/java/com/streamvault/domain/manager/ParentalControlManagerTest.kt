package com.streamvault.domain.manager

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ParentalControlManagerTest {

    private lateinit var manager: ParentalControlManager
    private lateinit var store: FakeParentalControlSessionStore

    @Before
    fun setup() {
        store = FakeParentalControlSessionStore()
        manager = ParentalControlManager(store)
    }

    @Test
    fun `initially no categories are unlocked`() {
        assertThat(manager.isCategoryUnlocked(1L, 1L)).isFalse()
        assertThat(manager.unlockedCategoriesByProvider.value).isEmpty()
    }

    @Test
    fun `unlockCategory makes category accessible`() {
        manager.unlockCategory(1L, 100L)
        assertThat(manager.isCategoryUnlocked(1L, 100L)).isTrue()
    }

    @Test
    fun `unlockCategory is scoped to provider`() {
        manager.unlockCategory(1L, 100L)
        assertThat(manager.isCategoryUnlocked(1L, 100L)).isTrue()
        assertThat(manager.isCategoryUnlocked(2L, 100L)).isFalse()
    }

    @Test
    fun `unlockCategory replaces previous unlocked category for provider`() {
        manager.unlockCategory(1L, 100L)
        manager.unlockCategory(1L, 200L)

        assertThat(manager.isCategoryUnlocked(1L, 100L)).isFalse()
        assertThat(manager.isCategoryUnlocked(1L, 200L)).isTrue()
    }

    @Test
    fun `clearUnlockedCategories with provider clears that provider only`() {
        manager.unlockCategory(1L, 100L)
        manager.unlockCategory(2L, 200L)

        manager.clearUnlockedCategories(1L)

        assertThat(manager.isCategoryUnlocked(1L, 100L)).isFalse()
        assertThat(manager.isCategoryUnlocked(2L, 200L)).isTrue()
    }

    @Test
    fun `clearUnlockedCategories without provider clears all`() {
        manager.unlockCategory(1L, 100L)
        manager.unlockCategory(2L, 200L)

        manager.clearUnlockedCategories()

        assertThat(manager.isCategoryUnlocked(1L, 100L)).isFalse()
        assertThat(manager.isCategoryUnlocked(2L, 200L)).isFalse()
        assertThat(manager.unlockedCategoriesByProvider.value).isEmpty()
    }

    @Test
    fun `unlockedCategoriesForProvider emits correct set`() = runTest {
        manager.unlockCategory(1L, 200L)

        val unlocked = manager.unlockedCategoriesForProvider(1L).first()
        assertThat(unlocked).containsExactly(200L)
    }

    @Test
    fun `unlockedCategoriesForProvider returns empty for unknown provider`() = runTest {
        val unlocked = manager.unlockedCategoriesForProvider(99L).first()
        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `unlocking same category twice is idempotent`() {
        manager.unlockCategory(1L, 100L)
        manager.unlockCategory(1L, 100L)
        assertThat(manager.isCategoryUnlocked(1L, 100L)).isTrue()
        assertThat(manager.unlockedCategoriesByProvider.value[1L]).hasSize(1)
    }

    @Test
    fun `retainUnlockedCategory keeps unlocked category when revisiting same category`() {
        manager.unlockCategory(1L, 100L)

        manager.retainUnlockedCategory(1L, 100L)

        assertThat(manager.isCategoryUnlocked(1L, 100L)).isTrue()
    }

    @Test
    fun `retainUnlockedCategory clears unlock when leaving category`() {
        manager.unlockCategory(1L, 100L)

        manager.retainUnlockedCategory(1L, 200L)

        assertThat(manager.isCategoryUnlocked(1L, 100L)).isFalse()
    }

    @Test
    fun `manager clears persisted unlock state on startup`() {
        store.state = ParentalControlSessionState(
            unlockedCategoryIdsByProvider = mapOf(1L to setOf(100L))
        )

        manager = ParentalControlManager(store)

        assertThat(store.state.unlockedCategoryIdsByProvider).isEmpty()
        assertThat(manager.unlockedCategoriesByProvider.value).isEmpty()
    }

    private class FakeParentalControlSessionStore : ParentalControlSessionStore {
        var state: ParentalControlSessionState = ParentalControlSessionState()

        override fun readSessionState(): ParentalControlSessionState = state

        override fun writeSessionState(state: ParentalControlSessionState) {
            this.state = state
        }
    }
}
