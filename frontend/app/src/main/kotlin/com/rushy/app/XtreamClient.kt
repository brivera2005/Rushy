package com.rushy.app

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class XtreamStreamDto(
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("series_id") val seriesId: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
)

data class XtreamCategoryDto(
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
)

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: XtreamUserInfo? = null,
)

data class XtreamUserInfo(
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
)

data class XtreamEpgResponse(
    @SerializedName("epg_listings") val listings: List<XtreamEpgListingDto>? = null,
)

data class XtreamEpgListingDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp") val stopTimestamp: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
)

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamAuthResponse

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
    ): List<XtreamStreamDto>

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
    ): List<XtreamStreamDto>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
    ): List<XtreamStreamDto>

    @GET("player_api.php")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: String,
        @Query("action") action: String = "get_short_epg",
        @Query("limit") limit: Int = 6,
    ): XtreamEpgResponse
}

class XtreamClient(
    portalUrl: String,
    private val username: String,
    private val password: String,
) {
    private val api: XtreamApiService

    init {
        val baseUrl = normalizePortalUrl(portalUrl)
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    suspend fun validateCredentials(): Boolean {
        val response = api.authenticate(username, password)
        val auth = response.userInfo?.auth
        return auth == 1 || response.userInfo?.status.equals("Active", ignoreCase = true)
    }

    suspend fun fetchLiveCategories(): List<ChannelCategory> {
        return api.getLiveCategories(username, password).mapNotNull { dto ->
            val id = dto.categoryId ?: return@mapNotNull null
            val name = dto.categoryName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChannelCategory(id = id, name = name)
        }
    }

    suspend fun fetchVodCategories(): List<ChannelCategory> {
        return api.getVodCategories(username, password).mapNotNull { dto ->
            val id = dto.categoryId ?: return@mapNotNull null
            val name = dto.categoryName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChannelCategory(id = id, name = name)
        }
    }

    suspend fun fetchLiveStreams(categoryMap: Map<String, String> = emptyMap()): List<MediaItem> {
        return api.getLiveStreams(username, password).mapNotNull { dto ->
            val id = dto.streamId ?: return@mapNotNull null
            val title = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val catId = dto.categoryId
            MediaItem(
                id = "xtream_live_$id",
                title = title,
                source = MediaSource.XTREAM_LIVE,
                playbackId = id.toString(),
                logoUrl = dto.streamIcon,
                categoryId = catId,
                categoryName = catId?.let { categoryMap[it] },
            )
        }
    }

    suspend fun fetchVodStreams(categoryMap: Map<String, String> = emptyMap()): List<MediaItem> {
        return api.getVodStreams(username, password).mapNotNull { dto ->
            val id = dto.streamId ?: return@mapNotNull null
            val title = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val catId = dto.categoryId
            MediaItem(
                id = "xtream_vod_$id",
                title = title,
                source = MediaSource.XTREAM_VOD,
                playbackId = id.toString(),
                logoUrl = dto.streamIcon ?: dto.cover,
                categoryId = catId,
                categoryName = catId?.let { categoryMap[it] },
            )
        }
    }

    suspend fun fetchSeries(categoryMap: Map<String, String> = emptyMap()): List<MediaItem> {
        return api.getSeries(username, password).mapNotNull { dto ->
            val id = dto.seriesId ?: return@mapNotNull null
            val title = dto.name?.takeIf { it.isNotBlank() }
                ?: dto.title?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val catId = dto.categoryId
            MediaItem(
                id = "xtream_series_$id",
                title = title,
                source = MediaSource.XTREAM_SERIES,
                playbackId = id.toString(),
                logoUrl = dto.cover ?: dto.streamIcon,
                categoryId = catId,
                categoryName = catId?.let { categoryMap[it] },
            )
        }
    }

    suspend fun fetchEpgForChannels(channels: List<MediaItem>): Map<String, List<EpgProgram>> {
        val result = mutableMapOf<String, List<EpgProgram>>()
        for (channel in channels) {
            runCatching {
                val response = api.getShortEpg(username, password, channel.playbackId)
                val programs = response.listings.orEmpty().mapNotNull { listing ->
                    listing.toEpgProgram(channel.playbackId)
                }
                if (programs.isNotEmpty()) {
                    result[channel.playbackId] = programs.sortedBy { it.startEpochSec }
                }
            }
        }
        return result
    }

    private fun XtreamEpgListingDto.toEpgProgram(fallbackChannelId: String): EpgProgram? {
        val programTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val startSec = start?.toLongOrNull() ?: parseTimestamp(startTimestamp) ?: return null
        val endSec = end?.toLongOrNull() ?: parseTimestamp(stopTimestamp) ?: (startSec + 3600)
        return EpgProgram(
            id = id ?: "${fallbackChannelId}_$startSec",
            channelId = channelId ?: fallbackChannelId,
            title = decodeBase64IfNeeded(programTitle),
            description = description?.let { decodeBase64IfNeeded(it) },
            startEpochSec = startSec,
            endEpochSec = endSec,
        )
    }

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            format.parse(value)?.time?.div(1000)
        }.getOrNull()
    }

    private fun decodeBase64IfNeeded(value: String): String {
        if (!value.startsWith("=") && value.length % 4 != 0) return value
        return runCatching {
            String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    companion object {
        fun normalizePortalUrl(url: String): String {
            var normalized = url.trim()
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "http://$normalized"
            }
            if (!normalized.endsWith("/")) {
                normalized = "$normalized/"
            }
            return normalized
        }
    }
}
