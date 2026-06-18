package com.streamvault.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamInfoTest {

    @Test
    fun expirationTime_preserves_non_negative_value() {
        val streamInfo = StreamInfo(
            url = "https://cdn.example.com/video.m3u8?expires=1774017000",
            expirationTime = 1_774_017_000_000L
        )

        assertThat(streamInfo.expirationTime).isEqualTo(1_774_017_000_000L)
    }

    @Test
    fun drmInfo_preserves_license_configuration() {
        val drmInfo = DrmInfo(
            scheme = DrmScheme.WIDEVINE,
            licenseUrl = "https://license.example.com/wv",
            headers = mapOf("Authorization" to "Bearer token"),
            multiSession = true,
            forceDefaultLicenseUrl = true,
            playClearContentWithoutKey = true
        )

        val streamInfo = StreamInfo(
            url = "https://cdn.example.com/movie.mpd",
            streamType = StreamType.DASH,
            drmInfo = drmInfo
        )

        assertThat(streamInfo.drmInfo).isEqualTo(drmInfo)
        assertThat(streamInfo.drmInfo?.scheme).isEqualTo(DrmScheme.WIDEVINE)
        assertThat(streamInfo.drmInfo?.multiSession).isTrue()
        assertThat(streamInfo.drmInfo?.forceDefaultLicenseUrl).isTrue()
        assertThat(streamInfo.drmInfo?.playClearContentWithoutKey).isTrue()
    }

    @Test
    fun drmInfo_rejects_disallowed_license_url() {
        val error = try {
            DrmInfo(
                scheme = DrmScheme.WIDEVINE,
                licenseUrl = "javascript:alert(1)"
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("allowed stream-entry URL scheme")
    }
}