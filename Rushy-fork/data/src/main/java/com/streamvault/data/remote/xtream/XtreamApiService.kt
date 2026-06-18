package com.streamvault.data.remote.xtream

import com.streamvault.data.remote.http.HttpRequestProfile
import com.streamvault.data.remote.dto.XtreamAuthResponse
import com.streamvault.data.remote.dto.XtreamCategory
import com.streamvault.data.remote.dto.XtreamEpgResponse
import com.streamvault.data.remote.dto.XtreamSeriesInfoResponse
import com.streamvault.data.remote.dto.XtreamSeriesItem
import com.streamvault.data.remote.dto.XtreamStream
import com.streamvault.data.remote.dto.XtreamVodInfoResponse

/**
 * Xtream Codes player API abstraction.
 */
interface XtreamApiService {
    suspend fun authenticate(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): XtreamAuthResponse

    suspend fun getLiveCategories(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamCategory>

    suspend fun getLiveStreams(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamStream>

    suspend fun getVodCategories(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamCategory>

    suspend fun getVodStreams(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamStream>

    suspend fun streamVodStreams(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile(),
        onItem: suspend (XtreamStream) -> Unit
    ): Int =
        getVodStreams(endpoint, requestProfile).also { streams ->
            streams.forEach { stream -> onItem(stream) }
        }.size

    suspend fun getVodInfo(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): XtreamVodInfoResponse

    suspend fun getSeriesCategories(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamCategory>

    suspend fun getSeriesList(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): List<XtreamSeriesItem>

    suspend fun streamSeriesList(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile(),
        onItem: suspend (XtreamSeriesItem) -> Unit
    ): Int =
        getSeriesList(endpoint, requestProfile).also { items ->
            items.forEach { item -> onItem(item) }
        }.size

    suspend fun getSeriesInfo(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): XtreamSeriesInfoResponse

    suspend fun getShortEpg(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): XtreamEpgResponse

    suspend fun getFullEpg(
        endpoint: String,
        requestProfile: HttpRequestProfile = HttpRequestProfile()
    ): XtreamEpgResponse
}
