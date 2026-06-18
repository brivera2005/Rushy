package com.streamvault.data.manager.recording

import android.content.ContentResolver
import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.RecordingSourceType
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class RecordingCaptureEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun hlsCaptureRetriesTransientSegmentFailureOnNextPlaylistRefresh() {
        runTest {
            val playlistBodies = listOf(
                """
                    #EXTM3U
                    #EXT-X-VERSION:3
                    #EXT-X-TARGETDURATION:2
                    #EXTINF:2.0,
                    https://example.com/segment-1.ts
                """.trimIndent(),
                """
                    #EXTM3U
                    #EXT-X-VERSION:3
                    #EXT-X-TARGETDURATION:2
                    #EXTINF:2.0,
                    https://example.com/segment-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent()
            )
            val playlistAttempts = AtomicInteger(0)
            val segmentAttempts = AtomicInteger(0)
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    when (chain.request().url.toString()) {
                        "https://example.com/live.m3u8" -> {
                            val attempt = (playlistAttempts.incrementAndGet() - 1)
                                .coerceAtMost(playlistBodies.lastIndex)
                            response(chain, 200, playlistBodies[attempt])
                        }
                        "https://example.com/segment-1.ts" -> {
                            if (segmentAttempts.incrementAndGet() == 1) {
                                throw IOException("socket timeout")
                            }
                            response(chain, 200, "segment-payload")
                        }
                        else -> error("Unexpected URL ${chain.request().url}")
                    }
                })
                .build()
            val engine = HlsLiveCaptureEngine(client)
            val outputFile = tempFolder.newFile("capture.ts")

            engine.capture(
                source = ResolvedRecordingSource(
                    url = "https://example.com/live.m3u8",
                    sourceType = RecordingSourceType.HLS
                ),
                outputTarget = RecordingOutputTarget.FileTarget(outputFile),
                contentResolver = mock<ContentResolver>(),
                scheduledEndMs = System.currentTimeMillis() + 60_000,
                onProgress = {}
            )

            assertThat(segmentAttempts.get()).isEqualTo(2)
            assertThat(outputFile.readText()).isEqualTo("segment-payload")
        }
    }

    private fun response(chain: Interceptor.Chain, code: Int, body: String): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
            .build()
}