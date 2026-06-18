package com.streamvault.data.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdultContentClassifierTest {

    @Test
    fun `cache uses bounded eviction instead of full flush`() {
        AdultContentClassifier.resetCacheForTesting()

        repeat(4096) { index ->
            AdultContentClassifier.isAdultCategoryName("Category $index")
        }
        AdultContentClassifier.isAdultCategoryName("Category 4095")
        AdultContentClassifier.isAdultCategoryName("Category 4096")

        assertThat(AdultContentClassifier.cacheSizeForTesting()).isEqualTo(4096)
        assertThat(AdultContentClassifier.isCachedForTesting("Category 4095")).isTrue()
        assertThat(AdultContentClassifier.isCachedForTesting("Category 0")).isFalse()
    }

    @Test
    fun `matches plus18 and adults labels used by xtream categories`() {
        assertThat(AdultContentClassifier.isAdultCategoryName("|+18| ADULTS LIVE")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("|+18| ADULTS")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("ADULT")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("ADULTS")).isTrue()
    }

    @Test
    fun `matches both 18plus orientations`() {
        assertThat(AdultContentClassifier.isAdultCategoryName("18+")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("+18")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("+ 18")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("18 +")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("Plus 18")).isTrue()
    }

    @Test
    fun `matching is case insensitive`() {
        assertThat(AdultContentClassifier.isAdultCategoryName("aDuLtS live")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("xXx premium")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("HaNiMe TV")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("MILF channel")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("Gay premium")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("LESBIAN world")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("Live Cam 4K")).isTrue()
        assertThat(AdultContentClassifier.isAdultCategoryName("LIVE CAMS HD")).isTrue()
    }

    @Test
    fun `boundary terms avoid obvious false positives`() {
        assertThat(AdultContentClassifier.isAdultCategoryName("Essex Sports")).isFalse()
    }
}
