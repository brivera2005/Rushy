package com.streamvault.data.sync

import com.streamvault.data.remote.xtream.XtreamAuthenticationException
import com.streamvault.data.remote.xtream.XtreamNetworkException
import com.streamvault.data.remote.xtream.XtreamParsingException
import com.streamvault.data.remote.xtream.XtreamRequestException
import com.streamvault.data.remote.xtream.XtreamResponseTooLargeException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal class SyncErrorSanitizer {
    fun userMessage(error: Throwable?, fallback: String): String {
        val projected = when (error) {
            null -> fallback
            is XtreamAuthenticationException -> "Provider authentication failed."
            is XtreamParsingException -> "Provider returned an unreadable response."
            is XtreamResponseTooLargeException -> "Provider returned an oversized catalog response."
            is XtreamRequestException -> classifyRequest(error.statusCode)
            is SocketTimeoutException,
            is InterruptedIOException -> "Provider timed out."
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            is SSLException -> "Provider connection failed."
            is SocketException -> "Provider connection was interrupted."
            is XtreamNetworkException -> "Provider connection failed."
            is IllegalStateException -> classifyIllegalState(error.message, fallback)
            else -> fallback
        }
        return sanitize(projected).ifBlank { fallback }
    }

    fun throwableMessage(error: Throwable?): String = sanitize(error?.message)

    fun sanitize(message: String?): String {
        if (message.isNullOrBlank()) {
            return "<empty>"
        }
        return message
            .replace(Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)) { match ->
                redactUrl(match.value)
            }
            .replace(Regex("""([?&](username|password|token|auth|key)=[^&\s]+)""", RegexOption.IGNORE_CASE), "<redacted-param>")
            .replace(Regex("""(?i)\b(user(name)?|password|token|auth|key)\s*[=:]\s*[^,\s]+"""), "<redacted-credential>")
    }

    private fun redactUrl(url: String): String {
        return runCatching {
            val parsed = URI(url)
            val scheme = parsed.scheme ?: "https"
            val host = parsed.host ?: return@runCatching "<redacted-url>"
            val path = parsed.path.orEmpty()
            "$scheme://$host$path"
        }.getOrDefault("<redacted-url>")
    }

    private fun classifyRequest(statusCode: Int): String {
        return when (statusCode) {
            401 -> "Provider authentication failed."
            403, 429 -> "Provider is temporarily busy."
            404 -> "Provider endpoint was not found."
            408 -> "Provider is temporarily busy."
            in 500..599 -> "Provider server is temporarily unavailable."
            else -> "Provider rejected the request."
        }
    }

    private fun classifyIllegalState(message: String?, fallback: String): String {
        val normalized = message.orEmpty().lowercase()
        return when {
            normalized.contains("no accessible catalog data") ->
                "Provider authenticated, but the account has no accessible catalog data. Check that the MAC is activated and assigned a package on the provider side."
            normalized.contains("empty") && normalized.contains("catalog") -> "Provider returned an empty catalog."
            normalized.contains("playlist") && normalized.contains("http") -> "Playlist download failed."
            normalized.contains("epg") -> "Guide download failed."
            normalized.contains("invalid") && normalized.contains("payload") -> "Provider returned invalid catalog data."
            normalized.contains("secure") || normalized.contains("insecure") -> "Provider URL was rejected by security checks."
            normalized.contains("authentication") -> "Provider authentication failed."
            else -> fallback
        }
    }
}
