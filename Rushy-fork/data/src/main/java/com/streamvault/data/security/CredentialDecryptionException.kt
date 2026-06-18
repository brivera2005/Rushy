package com.streamvault.data.security

class CredentialDecryptionException(
    message: String = MESSAGE,
    cause: Throwable? = null
) : IllegalStateException(message, cause) {
    companion object {
        const val MESSAGE = "Stored credentials are no longer readable. Please re-enter your provider credentials."
    }
}