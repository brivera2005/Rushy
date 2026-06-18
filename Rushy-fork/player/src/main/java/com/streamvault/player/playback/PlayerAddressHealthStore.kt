package com.streamvault.player.playback

import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal enum class PlayerAddressHealth {
    HEALTHY,
    UNKNOWN,
    UNHEALTHY
}

internal class PlayerAddressHealthStore(
    private val healthyTtlMs: Long = DEFAULT_HEALTHY_TTL_MS,
    val failureTtlMs: Long = DEFAULT_FAILURE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class AddressKey(
        val hostname: String,
        val port: Int,
        val address: String
    )

    private data class Entry(
        val health: PlayerAddressHealth,
        val updatedAtMs: Long
    )

    private val entries = ConcurrentHashMap<AddressKey, Entry>()

    fun markHealthy(hostname: String, port: Int, address: InetAddress?) {
        if (address == null) return
        entries[key(hostname, port, address)] = Entry(PlayerAddressHealth.HEALTHY, clock())
    }

    fun markUnhealthy(hostname: String, port: Int, address: InetAddress?) {
        if (address == null) return
        entries[key(hostname, port, address)] = Entry(PlayerAddressHealth.UNHEALTHY, clock())
    }

    fun health(hostname: String, port: Int, address: InetAddress): PlayerAddressHealth {
        val key = key(hostname, port, address)
        val entry = entries[key] ?: return PlayerAddressHealth.UNKNOWN
        val ttlMs = when (entry.health) {
            PlayerAddressHealth.HEALTHY -> healthyTtlMs
            PlayerAddressHealth.UNHEALTHY -> failureTtlMs
            PlayerAddressHealth.UNKNOWN -> 0L
        }
        if (clock() - entry.updatedAtMs > ttlMs) {
            entries.remove(key, entry)
            return PlayerAddressHealth.UNKNOWN
        }
        return entry.health
    }

    private fun key(hostname: String, port: Int, address: InetAddress): AddressKey {
        return AddressKey(
            hostname = hostname.lowercase(Locale.US),
            port = port,
            address = address.hostAddress.orEmpty()
        )
    }

    private companion object {
        const val DEFAULT_HEALTHY_TTL_MS = 10 * 60 * 1_000L
        const val DEFAULT_FAILURE_TTL_MS = 2 * 60 * 1_000L
    }
}
