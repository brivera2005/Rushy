package com.streamvault.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderRepositoryCatchUpTest {

    @Test
    fun `buildM3uCatchUpUrls expands common replay placeholders`() {
        val urls = buildM3uCatchUpUrls(
            source = "http://catchup.example.com/archive/{utc}/{utcend}/{Y}/{m}/{d}/{H}/{M}/{S}/{duration_minutes}.m3u8",
            start = 1_710_000_000L,
            end = 1_710_003_600L
        )

        assertThat(urls).containsExactly(
            "http://catchup.example.com/archive/1710000000/1710003600/2024/03/09/16/00/00/60.m3u8"
        )
    }
}
