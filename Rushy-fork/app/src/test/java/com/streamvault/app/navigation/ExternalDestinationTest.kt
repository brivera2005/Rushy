package com.streamvault.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExternalDestinationTest {

    @Test
    fun fromLegacyRoute_parsesSupportedRoutes() {
        assertThat(ExternalDestination.fromLegacyRoute("home"))
            .isEqualTo(ExternalDestination.Home)
        assertThat(ExternalDestination.fromLegacyRoute("provider_setup?providerId=-1&importUri="))
            .isEqualTo(ExternalDestination.ProviderSetup())
        assertThat(
            ExternalDestination.fromLegacyRoute(
                "series_detail/42?returnRoute=home"
            )
        ).isEqualTo(
            ExternalDestination.SeriesDetail(seriesId = 42L, returnRoute = "home")
        )
    }

    @Test
    fun fromLegacyRoute_rejectsUnsupportedRoutes() {
        assertThat(ExternalDestination.fromLegacyRoute("settings"))
            .isNull()
        assertThat(ExternalDestination.fromLegacyRoute("series_detail/not-a-number"))
            .isNull()
    }
}