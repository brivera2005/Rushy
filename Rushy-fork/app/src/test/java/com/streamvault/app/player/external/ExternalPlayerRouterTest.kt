package com.streamvault.app.player.external

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ProviderType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalPlayerRouterTest {

    @Test
    fun `toMpegTsUrl converts m3u8 to ts preserving query`() {
        val url = "https://cdn.example.com/live/12345.m3u8?token=abc"
        assertThat(ExternalPlayerRouter.toMpegTsUrl(url))
            .isEqualTo("https://cdn.example.com/live/12345.ts?token=abc")
    }

    @Test
    fun `toMpegTsUrl leaves ts URLs unchanged`() {
        val url = "https://cdn.example.com/live/12345.ts"
        assertThat(ExternalPlayerRouter.toMpegTsUrl(url)).isEqualTo(url)
    }

    @Test
    fun `buildTiviMateDeepLinkIntent uses watch scheme`() {
        val intent = ExternalPlayerRouter.buildTiviMateDeepLinkIntent(4242L)
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo(ExternalPlayerRouter.TIVIMATE_PACKAGE)
        assertThat(intent.data.toString()).isEqualTo("tivimate://watch?id=4242")
    }

    @Test
    fun `buildTiviMateDirectUrlIntent matches legacy Rushy ACTION_VIEW handoff`() {
        val intent = checkNotNull(
            ExternalPlayerRouter.buildTiviMateDirectUrlIntent(
                "http://portal.example/live/user/pass/4242.ts",
            )
        )
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo(ExternalPlayerRouter.TIVIMATE_PACKAGE)
        assertThat(intent.data.toString()).isEqualTo("http://portal.example/live/user/pass/4242.ts")
        assertThat(intent.type).isNull()
    }

    @Test
        assertThat(ExternalPlayerRouter.buildPackageViewIntent("  ", ExternalPlayerRouter.VLC_PACKAGE)).isNull()
    }

    @Test
    fun `buildPackageViewIntent sets target package`() {
        val intent = checkNotNull(
            ExternalPlayerRouter.buildPackageViewIntent(
                "https://cdn.example.com/live/1.ts",
                ExternalPlayerRouter.VLC_PACKAGE,
            )
        )
        assertThat(intent.`package`).isEqualTo(ExternalPlayerRouter.VLC_PACKAGE)
        assertThat(intent.type).isEqualTo("video/mp2t")
    }

    @Test
    fun `isLiveStyleUrl detects m3u8 and ts`() {
        assertThat(ExternalPlayerRouter.isLiveStyleUrl("https://cdn.example.com/live/1.m3u8?token=1")).isTrue()
        assertThat(ExternalPlayerRouter.isLiveStyleUrl("https://cdn.example.com/live/1.ts")).isTrue()
        assertThat(ExternalPlayerRouter.isLiveStyleUrl("https://cdn.example.com/movie.mp4")).isFalse()
    }

    @Test
    fun `shouldPlayVodExternally routes Plex and live-style Xtream`() {
        assertThat(
            ExternalPlayerRouter.shouldPlayVodExternally(
                ProviderType.PLEX,
                "https://plex.example.com/video.mp4",
            )
        ).isTrue()
        assertThat(
            ExternalPlayerRouter.shouldPlayVodExternally(
                ProviderType.XTREAM_CODES,
                "https://cdn.example.com/live/1.m3u8",
            )
        ).isTrue()
        assertThat(
            ExternalPlayerRouter.shouldPlayVodExternally(
                ProviderType.XTREAM_CODES,
                "https://cdn.example.com/movie.mp4",
            )
        ).isFalse()
    }
}
