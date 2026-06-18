package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Test

class PlayerDnsPolicyTest {

    @Test
    fun `sortForPlayback keeps all addresses when no health is known`() {
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val ipv4 = InetAddress.getByName("192.0.2.10") as Inet4Address

        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(ipv6, ipv4),
            healthStore = PlayerAddressHealthStore()
        )

        assertThat(sorted).containsExactly(ipv6, ipv4).inOrder()
    }

    @Test
    fun `sortForPlayback puts recently healthy addresses first regardless of address family`() {
        val ipv4a = InetAddress.getByName("192.0.2.10") as Inet4Address
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val ipv4b = InetAddress.getByName("192.0.2.20") as Inet4Address
        val healthStore = PlayerAddressHealthStore()

        healthStore.markHealthy("example.test", 443, ipv6)

        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(ipv4a, ipv6, ipv4b),
            healthStore = healthStore
        )

        assertThat(sorted).containsExactly(ipv6, ipv4a, ipv4b).inOrder()
    }

    @Test
    fun `sortForPlayback filters recently failed addresses regardless of address family`() {
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val ipv4 = InetAddress.getByName("192.0.2.10") as Inet4Address
        val healthStore = PlayerAddressHealthStore()

        healthStore.markUnhealthy("example.test", 443, ipv4)

        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(ipv4, ipv6),
            healthStore = healthStore
        )

        assertThat(sorted).containsExactly(ipv6)
    }

    @Test
    fun `sortForPlayback filters recently failed addresses when healthier alternatives exist`() {
        val failedIpv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val unknownIpv4 = InetAddress.getByName("192.0.2.10") as Inet4Address
        val healthStore = PlayerAddressHealthStore()

        healthStore.markUnhealthy("example.test", 443, failedIpv6)

        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(failedIpv6, unknownIpv4),
            healthStore = healthStore
        )

        assertThat(sorted).containsExactly(unknownIpv4)
    }

    @Test
    fun `sortForPlayback keeps failed addresses when every address is failed`() {
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val ipv4 = InetAddress.getByName("192.0.2.10") as Inet4Address
        val healthStore = PlayerAddressHealthStore()

        healthStore.markUnhealthy("example.test", 443, ipv6)
        healthStore.markUnhealthy("example.test", 443, ipv4)

        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(ipv6, ipv4),
            healthStore = healthStore
        )

        assertThat(sorted).containsExactly(ipv6, ipv4).inOrder()
    }

    @Test
    fun `sortForPlayback forgets unhealthy addresses after failure ttl`() {
        var nowMs = 1_000L
        val ipv6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val ipv4 = InetAddress.getByName("192.0.2.10") as Inet4Address
        val healthStore = PlayerAddressHealthStore(
            failureTtlMs = 1_000L,
            clock = { nowMs }
        )

        healthStore.markUnhealthy("example.test", 443, ipv4)

        nowMs = 2_001L
        val sorted = PlayerDnsPolicy.sortForPlayback(
            hostname = "example.test",
            port = 443,
            addresses = listOf(ipv4, ipv6),
            healthStore = healthStore
        )

        assertThat(sorted).containsExactly(ipv4, ipv6).inOrder()
    }
}
