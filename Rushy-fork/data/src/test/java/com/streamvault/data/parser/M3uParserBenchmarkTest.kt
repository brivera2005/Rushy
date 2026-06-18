package com.streamvault.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M3uParserBenchmarkTest {
    private val parser = M3uParser()

    @Test
    fun `benchmark parser synthetic playlists`() {
        val sizes = listOf(10_000, 50_000, 100_000)
        val timings = linkedMapOf<Int, Long>()

        sizes.forEach { size ->
            val playlist = buildPlaylist(size)
            val samples = mutableListOf<Long>()
            repeat(3) {
                val started = System.nanoTime()
                val result = parser.parse(playlist.byteInputStream())
                samples += System.nanoTime() - started
                assertThat(result.entries).hasSize(size)
            }
            timings[size] = samples.sorted()[samples.size / 2]
        }

        println(
            timings.entries.joinToString(prefix = "M3U parser median timings: ") { (size, nanos) ->
                "$size=${nanos / 1_000_000}ms"
            }
        )
    }

    private fun buildPlaylist(size: Int): String {
        val builder = StringBuilder("#EXTM3U\n")
        repeat(size) { index ->
            builder.append("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"Group ${index % 20}\",Channel ${index + 1}\n")
            builder.append("http://stream.example.com/ch${index + 1}.ts\n")
        }
        return builder.toString()
    }
}
