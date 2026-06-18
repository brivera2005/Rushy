package com.streamvault.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentCachePolicyTest {

    @Test
    fun `shouldRefresh returns true when timestamp is in the future`() {
        val now = 1_000L
        val lastSyncAt = 2_000L

        val result = ContentCachePolicy.shouldRefresh(
            lastSyncAt = lastSyncAt,
            ttlMillis = 60_000L,
            now = now
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRefresh returns false when ttl has not expired`() {
        val now = 10_000L
        val lastSyncAt = 9_000L

        val result = ContentCachePolicy.shouldRefresh(
            lastSyncAt = lastSyncAt,
            ttlMillis = 5_000L,
            now = now
        )

        assertThat(result).isFalse()
    }
}
