package com.streamvault.player.playback

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.DrmInfo
import com.streamvault.domain.model.DrmScheme
import com.streamvault.domain.model.StreamInfo
import java.lang.reflect.Proxy
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Test

class PreloadCoordinatorTest {

    @Test
    fun `same item same url under two minutes reuses`() {
        var now = 1_000L
        val coordinator = PreloadCoordinator { now }
        val source = fakeMediaSource()
        val info = StreamInfo(url = "http://example.com/movie.mp4")
        coordinator.store("1", info, ResolvedStreamType.PROGRESSIVE, source)

        assertThat(coordinator.tryReuse("1", info, ResolvedStreamType.PROGRESSIVE)).isSameInstanceAs(source)
    }

    @Test
    fun `different url invalidates`() {
        var now = 1_000L
        val coordinator = PreloadCoordinator { now }
        coordinator.store("1", StreamInfo(url = "http://example.com/a.mp4"), ResolvedStreamType.PROGRESSIVE, fakeMediaSource())

        assertThat(coordinator.tryReuse("1", StreamInfo(url = "http://example.com/b.mp4"), ResolvedStreamType.PROGRESSIVE)).isNull()
    }

    @Test
    fun `different stream type invalidates`() {
        var now = 1_000L
        val coordinator = PreloadCoordinator { now }
        val info = StreamInfo(url = "http://example.com/live/channel.ts")
        coordinator.store("1", info, ResolvedStreamType.MPEG_TS_LIVE, fakeMediaSource())

        assertThat(coordinator.tryReuse("1", info, ResolvedStreamType.HLS)).isNull()
    }

    @Test
    fun `older than two minutes expires`() {
        var now = 1_000L
        val coordinator = PreloadCoordinator { now }
        val info = StreamInfo(
            url = "http://example.com/movie.mp4",
            drmInfo = DrmInfo(DrmScheme.WIDEVINE, "http://license")
        )
        coordinator.store("1", info, ResolvedStreamType.PROGRESSIVE, fakeMediaSource())
        now += 121_000L

        assertThat(coordinator.tryReuse("1", info, ResolvedStreamType.PROGRESSIVE)).isNull()
    }

    private fun fakeMediaSource(): MediaSource {
        return Proxy.newProxyInstance(
            MediaSource::class.java.classLoader,
            arrayOf(MediaSource::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Unit::class.java -> Unit
                else -> null
            }
        } as MediaSource
    }
}
