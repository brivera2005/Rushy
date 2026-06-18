package com.streamvault.data.remote.xtream

import com.streamvault.data.security.CredentialDecryptionException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

internal object XtreamErrorFormatter {
    fun message(prefix: String, throwable: Throwable): String {
        val formatted = when {
            throwable is CredentialDecryptionException -> "$prefix: ${throwable.message}"
            throwable.isCertificateTrustFailure() -> "$prefix: Server TLS certificate is not trusted by this device. Verify the HTTPS URL or ask the provider for a valid certificate."
            throwable is XtreamAuthenticationException -> "$prefix: Authentication failed. Please check your username, password, and server URL."
            throwable is XtreamParsingException -> "$prefix: ${throwable.message ?: "Server returned malformed or unsupported data."}"
            throwable is XtreamRequestException -> "$prefix: Request failed with HTTP ${throwable.statusCode}."
            else -> "$prefix: ${throwable.message ?: "Unexpected network error"}"
        }
        return XtreamUrlFactory.sanitizeLogMessage(formatted)
    }

    private fun Throwable.isCertificateTrustFailure(): Boolean {
        return generateSequence(this) { it.cause }.any { current ->
            current is SSLPeerUnverifiedException ||
                current is CertificateException ||
                current.message?.contains("trust anchor", ignoreCase = true) == true ||
                current.message?.contains("certificate", ignoreCase = true) == true ||
                current.message?.contains("hostname", ignoreCase = true) == true
        }
    }
}
