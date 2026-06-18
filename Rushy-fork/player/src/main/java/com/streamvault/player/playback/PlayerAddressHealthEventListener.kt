package com.streamvault.player.playback

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol

internal class PlayerAddressHealthEventListener(
    private val healthStore: PlayerAddressHealthStore
) : EventListener() {
    private data class ConnectedAddress(
        val hostname: String,
        val port: Int,
        val address: java.net.InetAddress?
    )

    private val connectedAddresses = ConcurrentHashMap<Call, ConnectedAddress>()

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        val connectedAddress = ConnectedAddress(
            hostname = call.request().url.host,
            port = inetSocketAddress.port,
            address = inetSocketAddress.address
        )
        connectedAddresses[call] = connectedAddress
        healthStore.markHealthy(
            hostname = connectedAddress.hostname,
            port = connectedAddress.port,
            address = connectedAddress.address
        )
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        healthStore.markUnhealthy(
            hostname = call.request().url.host,
            port = inetSocketAddress.port,
            address = inetSocketAddress.address
        )
    }

    override fun callEnd(call: Call) {
        connectedAddresses.remove(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val connectedAddress = connectedAddresses.remove(call) ?: return
        healthStore.markUnhealthy(
            hostname = connectedAddress.hostname,
            port = connectedAddress.port,
            address = connectedAddress.address
        )
    }
}
