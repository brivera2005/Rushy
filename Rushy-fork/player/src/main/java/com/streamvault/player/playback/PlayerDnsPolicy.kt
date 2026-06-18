package com.streamvault.player.playback

import java.net.InetAddress
import okhttp3.Dns

internal object PlayerDnsPolicy {
    fun healthAwareDns(
        port: Int,
        healthStore: PlayerAddressHealthStore,
        delegate: Dns = Dns.SYSTEM
    ): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return sortForPlayback(
                    hostname = hostname,
                    port = port,
                    addresses = delegate.lookup(hostname),
                    healthStore = healthStore
                )
            }
        }
    }

    fun sortForPlayback(
        hostname: String,
        port: Int,
        addresses: List<InetAddress>,
        healthStore: PlayerAddressHealthStore
    ): List<InetAddress> {
        val addressesWithHealth = addresses.map { address ->
            address to healthStore.health(hostname, port, address)
        }
        val hasHealthierAlternative = addressesWithHealth.any { (_, health) ->
            health != PlayerAddressHealth.UNHEALTHY
        }
        return addressesWithHealth
            .let { entries ->
                if (hasHealthierAlternative) {
                    entries.filterNot { (_, health) -> health == PlayerAddressHealth.UNHEALTHY }
                } else {
                    entries
                }
            }
            .sortedBy { (_, health) ->
                when (health) {
                    PlayerAddressHealth.HEALTHY -> 0
                    PlayerAddressHealth.UNKNOWN -> 1
                    PlayerAddressHealth.UNHEALTHY -> 2
                }
            }
            .map { (address, _) -> address }
    }
}
