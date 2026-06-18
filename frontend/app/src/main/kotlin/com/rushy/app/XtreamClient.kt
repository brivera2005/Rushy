package com.rushy.app



import android.util.Log

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

    @SerializedName("plot") val plot: String? = null,

    @SerializedName("rating") val rating: String? = null,

    @SerializedName("rating_5based") val rating5Based: Double? = null,

    @SerializedName("epg_channel_id") val epgChannelId: String? = null,

    @SerializedName("tv_archive") val tvArchive: Int? = null,

    @SerializedName("tv_archive_duration") val tvArchiveDuration: String? = null,

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

        @Query("category_id") categoryId: String? = null,

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

        @Query("category_id") categoryId: String? = null,

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

        @Query("category_id") categoryId: String? = null,

    ): List<XtreamStreamDto>



    @GET("player_api.php")

    suspend fun getSeriesCategories(

        @Query("username") username: String,

        @Query("password") password: String,

        @Query("action") action: String = "get_series_categories",

    ): List<XtreamCategoryDto>



    @GET("player_api.php")

    suspend fun getShortEpg(

        @Query("username") username: String,

        @Query("password") password: String,

        @Query("stream_id") streamId: String,

        @Query("action") action: String = "get_short_epg",

        @Query("limit") limit: Int = 4,

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

            .connectTimeout(30, TimeUnit.SECONDS)

            .readTimeout(180, TimeUnit.SECONDS)

            .writeTimeout(30, TimeUnit.SECONDS)

            .build()



        api = Retrofit.Builder()

            .baseUrl(baseUrl)

            .client(client)

            .addConverterFactory(GsonConverterFactory.create())

            .build()

            .create(XtreamApiService::class.java)

    }



    suspend fun validateCredentials(): Boolean {

        return runCatching {

            val response = api.authenticate(username, password)

            val auth = response.userInfo?.auth

            auth == 1 || response.userInfo?.status.equals("Active", ignoreCase = true)

        }.getOrElse { e ->

            Log.e(TAG, "Credential validation failed", e)

            false

        }

    }



    suspend fun fetchLiveCategories(): List<ChannelCategory> {

        return runCatching {

            api.getLiveCategories(username, password).mapNotNull { dto ->

                val id = dto.categoryId ?: return@mapNotNull null

                val name = dto.categoryName?.takeIf { it.isNotBlank() } ?: "Category $id"

                ChannelCategory(id = id, name = name)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchLiveCategories failed", e)

            emptyList()

        }

    }



    suspend fun fetchVodCategories(): List<ChannelCategory> {

        return runCatching {

            api.getVodCategories(username, password).mapNotNull { dto ->

                val id = dto.categoryId ?: return@mapNotNull null

                val name = dto.categoryName?.takeIf { it.isNotBlank() } ?: "Category $id"

                ChannelCategory(id = id, name = name)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchVodCategories failed", e)

            emptyList()

        }

    }



    suspend fun fetchLiveStreams(

        categoryMap: Map<String, String> = emptyMap(),

        categoryId: String? = null,

    ): List<MediaItem> {

        return runCatching {

            api.getLiveStreams(username, password, categoryId = categoryId).mapNotNull { dto ->

                dto.toLiveMediaItem(categoryMap)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchLiveStreams failed (category=$categoryId)", e)

            if (categoryId != null) {

                emptyList()

            } else {

                throw SyncException("Failed to load live channels: ${e.message}", e)

            }

        }

    }



    suspend fun fetchSeriesCategories(): List<ChannelCategory> {

        return runCatching {

            api.getSeriesCategories(username, password).mapNotNull { dto ->

                val id = dto.categoryId ?: return@mapNotNull null

                val name = dto.categoryName?.takeIf { it.isNotBlank() } ?: "Category $id"

                ChannelCategory(id = id, name = name)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchSeriesCategories failed", e)

            emptyList()

        }

    }



    suspend fun fetchLiveStreamsByCategory(

        categories: List<ChannelCategory>,

        onCategory: suspend (categoryName: String, items: List<MediaItem>) -> Unit,

    ): Int {

        var total = 0

        for (category in categories) {

            val items = fetchLiveStreams(mapOf(category.id to category.name), category.id)

            if (items.isNotEmpty()) {

                onCategory(category.name, items)

                total += items.size

            }

        }

        if (total == 0 && categories.isNotEmpty()) {

            Log.w(TAG, "Per-category live fetch returned 0; falling back to full download")

            val catMap = categories.associate { it.id to it.name }

            val all = fetchLiveStreams(catMap, categoryId = null)

            all.groupBy { it.categoryId ?: "unknown" }.forEach { (catId, items) ->

                val name = catMap[catId] ?: "Category $catId"

                onCategory(name, items)

            }

            return all.size

        }

        return total

    }



    suspend fun fetchVodStreams(

        categoryMap: Map<String, String> = emptyMap(),

        categoryId: String? = null,

    ): List<MediaItem> {

        return runCatching {

            api.getVodStreams(username, password, categoryId = categoryId).mapNotNull { dto ->

                dto.toVodMediaItem(categoryMap)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchVodStreams failed (category=$categoryId)", e)

            if (categoryId != null) {

                emptyList()

            } else {

                throw SyncException("Failed to load movies: ${e.message}", e)

            }

        }

    }



    suspend fun fetchVodStreamsByCategory(

        categories: List<ChannelCategory>,

        onCategory: suspend (categoryName: String, items: List<MediaItem>) -> Unit,

    ): Int {

        var total = 0

        for (category in categories) {

            val items = fetchVodStreams(mapOf(category.id to category.name), category.id)

            if (items.isNotEmpty()) {

                onCategory(category.name, items)

                total += items.size

            }

        }

        if (total == 0 && categories.isNotEmpty()) {

            Log.w(TAG, "Per-category VOD fetch returned 0; falling back to full download")

            val catMap = categories.associate { it.id to it.name }

            val all = fetchVodStreams(catMap, categoryId = null)

            all.groupBy { it.categoryId ?: "unknown" }.forEach { (catId, items) ->

                val name = catMap[catId] ?: "Category $catId"

                onCategory(name, items)

            }

            return all.size

        }

        return total

    }



    suspend fun fetchSeries(

        categoryMap: Map<String, String> = emptyMap(),

        categoryId: String? = null,

    ): List<MediaItem> {

        return runCatching {

            api.getSeries(username, password, categoryId = categoryId).mapNotNull { dto ->

                dto.toSeriesMediaItem(categoryMap)

            }

        }.getOrElse { e ->

            Log.e(TAG, "fetchSeries failed (category=$categoryId)", e)

            if (categoryId != null) {

                emptyList()

            } else {

                throw SyncException("Failed to load series: ${e.message}", e)

            }

        }

    }



    suspend fun fetchSeriesByCategory(

        categories: List<ChannelCategory>,

        onCategory: suspend (categoryName: String, items: List<MediaItem>) -> Unit,

    ): Int {

        var total = 0

        for (category in categories) {

            val items = fetchSeries(mapOf(category.id to category.name), category.id)

            if (items.isNotEmpty()) {

                onCategory(category.name, items)

                total += items.size

            }

        }

        if (total == 0 && categories.isNotEmpty()) {

            Log.w(TAG, "Per-category series fetch returned 0; falling back to full download")

            val catMap = categories.associate { it.id to it.name }

            val all = fetchSeries(catMap, categoryId = null)

            all.groupBy { it.categoryId ?: "unknown" }.forEach { (catId, items) ->

                val name = catMap[catId] ?: "Category $catId"

                onCategory(name, items)

            }

            return all.size

        }

        return total

    }



    suspend fun fetchEpgForChannel(channel: MediaItem): List<EpgProgram> {

        return runCatching {

            val response = api.getShortEpg(username, password, channel.playbackId)

            response.listings.orEmpty().mapNotNull { listing ->

                listing.toEpgProgram(channel.playbackId)

            }.sortedBy { it.startEpochSec }

        }.getOrElse { e ->

            Log.w(TAG, "EPG fetch failed for ${channel.playbackId}", e)

            emptyList()

        }

    }



    private fun XtreamStreamDto.toLiveMediaItem(categoryMap: Map<String, String>): MediaItem? {

        val id = streamId ?: return null

        val itemTitle = name?.takeIf { it.isNotBlank() } ?: return null

        val catId = categoryId?.takeIf { it.isNotBlank() }

        return MediaItem(

            id = "xtream_live_$id",

            title = itemTitle,

            source = MediaSource.XTREAM_LIVE,

            playbackId = id.toString(),

            logoUrl = streamIcon?.takeIf { it.isNotBlank() },

            categoryId = catId,

            categoryName = catId?.let { categoryMap[it] },

            epgChannelId = epgChannelId?.takeIf { it.isNotBlank() },

            tvArchive = tvArchive == 1,

            tvArchiveDurationHours = tvArchiveDuration?.toIntOrNull()?.takeIf { it > 0 },

        )

    }



    private fun XtreamStreamDto.toVodMediaItem(categoryMap: Map<String, String>): MediaItem? {

        val id = streamId ?: return null

        val itemTitle = name?.takeIf { it.isNotBlank() } ?: return null

        val catId = categoryId?.takeIf { it.isNotBlank() }

        return MediaItem(

            id = "xtream_vod_$id",

            title = itemTitle,

            source = MediaSource.XTREAM_VOD,

            playbackId = id.toString(),

            logoUrl = streamIcon?.takeIf { it.isNotBlank() } ?: cover?.takeIf { it.isNotBlank() },

            categoryId = catId,

            categoryName = catId?.let { categoryMap[it] },

            plot = plot?.takeIf { it.isNotBlank() },

            rating = formatRating(rating, rating5Based),

        )

    }



    private fun XtreamStreamDto.toSeriesMediaItem(categoryMap: Map<String, String>): MediaItem? {

        val id = seriesId ?: return null

        val itemTitle = name?.takeIf { it.isNotBlank() }

            ?: title?.takeIf { it.isNotBlank() }

            ?: return null

        val catId = categoryId?.takeIf { it.isNotBlank() }

        return MediaItem(

            id = "xtream_series_$id",

            title = itemTitle,

            source = MediaSource.XTREAM_SERIES,

            playbackId = id.toString(),

            logoUrl = cover?.takeIf { it.isNotBlank() } ?: streamIcon?.takeIf { it.isNotBlank() },

            categoryId = catId,

            categoryName = catId?.let { categoryMap[it] },

            plot = plot?.takeIf { it.isNotBlank() },

            rating = formatRating(rating, rating5Based),

        )

    }



    private fun formatRating(rating: String?, rating5Based: Double?): String? {

        rating?.takeIf { it.isNotBlank() }?.let { return it }

        rating5Based?.let { return String.format("%.1f", it) }

        return null

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

        private const val TAG = "XtreamClient"



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


