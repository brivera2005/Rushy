package com.streamvault.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.manager.ParentalControlManager
import com.streamvault.domain.manager.ParentalControlSessionState
import com.streamvault.domain.manager.ParentalControlSessionStore
import com.streamvault.domain.manager.ParentalPinVerifier
import com.streamvault.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UnlockParentalCategoryTest {

    @Test
    fun unlocks_category_when_pin_is_valid() = runTest {
        val useCase = UnlockParentalCategory(
            parentalPinVerifier = FakeParentalPinVerifier(valid = true),
            parentalControlManager = ParentalControlManager(InMemorySessionStore())
        )

        val result = useCase(
            UnlockParentalCategoryCommand(
                providerId = 7L,
                categoryId = 42L,
                pin = "1234"
            )
        )

        assertThat(result is Result.Success).isTrue()
    }

    @Test
    fun rejects_invalid_pin() = runTest {
        val useCase = UnlockParentalCategory(
            parentalPinVerifier = FakeParentalPinVerifier(valid = false),
            parentalControlManager = ParentalControlManager(InMemorySessionStore())
        )

        val result = useCase(
            UnlockParentalCategoryCommand(
                providerId = 7L,
                categoryId = 42L,
                pin = "0000"
            )
        )

        assertThat((result as Result.Error).message).isEqualTo("Incorrect PIN")
    }

    @Test
    fun rejects_missing_context() = runTest {
        val useCase = UnlockParentalCategory(
            parentalPinVerifier = FakeParentalPinVerifier(valid = true),
            parentalControlManager = ParentalControlManager(InMemorySessionStore())
        )

        val result = useCase(
            UnlockParentalCategoryCommand(
                providerId = 0L,
                categoryId = 0L,
                pin = "1234"
            )
        )

        assertThat((result as Result.Error).message).isEqualTo("Locked category context is unavailable.")
    }
}

private class FakeParentalPinVerifier(
    private val valid: Boolean
) : ParentalPinVerifier {
    override suspend fun verifyParentalPin(pin: String): Boolean = valid
}

private class InMemorySessionStore : ParentalControlSessionStore {
    private var state = ParentalControlSessionState()

    override fun readSessionState(): ParentalControlSessionState = state

    override fun writeSessionState(state: ParentalControlSessionState) {
        this.state = state
    }
}