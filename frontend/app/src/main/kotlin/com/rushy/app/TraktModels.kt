package com.rushy.app

import com.google.gson.annotations.SerializedName

data class TraktTrendingMovieEntry(
    val watchers: Int = 0,
    val movie: TraktMovie? = null,
)

data class TraktTrendingShowEntry(
    val watchers: Int = 0,
    val show: TraktShow? = null,
)

data class TraktMovie(
    val title: String = "",
    val year: Int = 0,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val rating: Double = 0.0,
    val images: TraktImages? = null,
)

data class TraktShow(
    val title: String = "",
    val year: Int = 0,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val rating: Double = 0.0,
    val images: TraktImages? = null,
)

data class TraktIds(
    val trakt: Int = 0,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
)

data class TraktImages(
    val poster: List<String>? = null,
    val fanart: List<String>? = null,
)

data class TraktDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String = "",
    @SerializedName("user_code") val userCode: String = "",
    @SerializedName("verification_url") val verificationUrl: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5,
)

data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0,
    val scope: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
)

data class TraktOAuthError(
    val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
)

sealed class TraktAuthState {
    data object Disconnected : TraktAuthState()
    data object WaitingForUser : TraktAuthState()
    data object Polling : TraktAuthState()
    data class Connected(val username: String? = null) : TraktAuthState()
    data class Error(val message: String) : TraktAuthState()
}

data class TraktDeviceAuthSession(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val expiresAtMs: Long,
    val pollIntervalSec: Int,
)
