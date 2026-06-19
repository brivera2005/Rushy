package com.streamvault.app.player.external

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LivePlaybackRouterTest {

    @Test
    fun `toMpegTsUrl converts m3u8 to ts preserving query`() {
        val url = "https://cdn.example.com/live/12345.m3u8?token=abc"
        assertThat(LivePlaybackRouter.toMpegTsUrl(url))
            .isEqualTo("https://cdn.example.com/live/12345.ts?token=abc")
    }

    @Test
    fun `toMpegTsUrl leaves ts URLs unchanged`() {
        val url = "https://cdn.example.com/live/12345.ts"
        assertThat(LivePlaybackRouter.toMpegTsUrl(url)).isEqualTo(url)
    }

    @Test
    fun `buildTiviMateDeepLinkIntent uses watch scheme`() {
        val intent = LivePlaybackRouter.buildTiviMateDeepLinkIntent(4242L)
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo(LivePlaybackRouter.TIVIMATE_PACKAGE)
        assertThat(intent.data.toString()).isEqualTo("tivimate://watch?id=4242")
    }

    @Test
    fun `buildPackageViewIntent returns null for blank URL`() {
        assertThat(LivePlaybackRouter.buildPackageViewIntent("  ", LivePlaybackRouter.VLC_PACKAGE)).isNull()
    }

    @Test
    fun `buildPackageViewIntent sets target package`() {
        val intent = checkNotNull(
            LivePlaybackRouter.buildPackageViewIntent(
                "https://cdn.example.com/live/1.ts",
                LivePlaybackRouter.VLC_PACKAGE
            )
        )
        assertThat(intent.`package`).isEqualTo(LivePlaybackRouter.VLC_PACKAGE)
        assertThat(intent.type).isEqualTo("video/mp2t")
    }
}
