package com.streamvault.domain.provider

import com.streamvault.domain.model.*

/**
 * Shared interface for all IPTV providers (Xtream Codes, M3U, etc.).
 * Each provider type implements this to normalize data into domain models.
 */
interface IptvProvider {
    val providerId: Long

    suspend fun authenticate(): Result<Provider>

    suspend fun getLiveCategories(): Result<List<Category>>
    suspend fun getLiveStreams(categoryId: Long? = null): Result<List<Channel>>

    suspend fun getVodCategories(): Result<List<Category>>
    suspend fun getVodStreams(categoryId: Long? = null): Result<List<Movie>>
    suspend fun getVodInfo(vodId: Long): Result<Movie>

    suspend fun getSeriesCategories(): Result<List<Category>>
    suspend fun getSeriesList(categoryId: Long? = null): Result<List<Series>>
    suspend fun getSeriesInfo(seriesId: Long): Result<Series>

    suspend fun getEpg(channelId: String): Result<List<Program>>
    suspend fun getShortEpg(channelId: String, limit: Int = 4): Result<List<Program>>

    suspend fun buildStreamUrl(streamId: Long, containerExtension: String? = null): String
    suspend fun buildCatchUpUrl(streamId: Long, start: Long, end: Long): String?
    suspend fun buildCatchUpUrls(streamId: Long, start: Long, end: Long): List<String> =
        listOfNotNull(buildCatchUpUrl(streamId, start, end))
}
