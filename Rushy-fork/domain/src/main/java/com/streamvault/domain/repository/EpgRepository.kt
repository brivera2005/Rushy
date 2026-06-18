package com.streamvault.domain.repository

import com.streamvault.domain.model.Program
import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    fun getProgramsForChannel(providerId: Long, channelId: String, startTime: Long, endTime: Long): Flow<List<Program>>
    fun getProgramsForChannels(providerId: Long, channelIds: List<String>, startTime: Long, endTime: Long): Flow<Map<String, List<Program>>>
    suspend fun getProgramsForChannelsSnapshot(providerId: Long, channelIds: List<String>, startTime: Long, endTime: Long): Map<String, List<Program>>
    fun getProgramsByCategory(providerId: Long, categoryId: Long, startTime: Long, endTime: Long): Flow<List<Program>>
    fun searchPrograms(providerId: Long, query: String, startTime: Long, endTime: Long, categoryId: Long? = null, limit: Int = 250): Flow<List<Program>>
    fun getNowPlaying(providerId: Long, channelId: String): Flow<Program?>
    fun getNowPlayingForChannels(providerId: Long, channelIds: List<String>): Flow<Map<String, Program?>>
    suspend fun getNowPlayingForChannelsSnapshot(providerId: Long, channelIds: List<String>): Map<String, Program?>
    fun getNowAndNext(providerId: Long, channelId: String): Flow<Pair<Program?, Program?>>
    suspend fun refreshEpg(providerId: Long, epgUrl: String): Result<Unit>
    suspend fun clearOldPrograms(beforeTime: Long)
    fun onProviderDeleted(providerId: Long)

    /** Returns resolved EPG programmes using multi-source resolution (external + provider-native). */
    suspend fun getResolvedProgramsForChannels(
        providerId: Long,
        channelIds: List<Long>,
        startTime: Long,
        endTime: Long
    ): Map<String, List<Program>>

    suspend fun getResolvedProgramsForPlaybackChannel(
        providerId: Long,
        internalChannelId: Long,
        epgChannelId: String?,
        streamId: Long,
        startTime: Long,
        endTime: Long
    ): List<Program>
}
