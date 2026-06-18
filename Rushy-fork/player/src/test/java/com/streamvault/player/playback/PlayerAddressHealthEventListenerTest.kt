package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Test

class PlayerAddressHealthEventListenerTest {

    @Test
    fun `callFailed marks the last connected address unhealthy`() {
        val address = InetAddress.getByName("192.0.2.10")
        val socketAddress = InetSocketAddress(address, 443)
        val healthStore = PlayerAddressHealthStore()
        val listener = PlayerAddressHealthEventListener(healthStore)
        val call = FakeCall("https://example.test/movie.mkv")

        listener.connectEnd(call, socketAddress, Proxy.NO_PROXY, Protocol.HTTP_1_1)
        assertThat(healthStore.health("example.test", 443, address))
            .isEqualTo(PlayerAddressHealth.HEALTHY)

        listener.callFailed(call, IOException("read timeout"))

        assertThat(healthStore.health("example.test", 443, address))
            .isEqualTo(PlayerAddressHealth.UNHEALTHY)
    }

    private class FakeCall(url: String) : Call {
        private val request = Request.Builder().url(url).build()

        override fun request(): Request = request

        override fun execute(): Response {
            error("Not used")
        }

        override fun enqueue(responseCallback: Callback) {
            error("Not used")
        }

        override fun cancel() = Unit

        override fun isExecuted(): Boolean = false

        override fun isCanceled(): Boolean = false

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall(request.url.toString())
    }
}
