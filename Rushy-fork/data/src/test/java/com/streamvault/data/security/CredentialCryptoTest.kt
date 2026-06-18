package com.streamvault.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialCryptoTest {

    @Test
    fun `credential decryption exception exposes actionable default message`() {
        val failure = CredentialDecryptionException()

        assertThat(failure.message).contains("Please re-enter your provider credentials")
    }
}