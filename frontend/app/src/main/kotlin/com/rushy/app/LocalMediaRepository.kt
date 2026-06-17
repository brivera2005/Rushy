package com.rushy.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

class LocalMediaRepository private constructor(
    private val context: Context,
    private val credentials: CredentialStore,
) {
    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "media_cache.json")
    private val flagsFile = File(context.filesDir, "media_flags.json")

    private var cachedItems: List<MediaItem> = emptyList()
    private var itemFlags: MutableMap<String, ItemFlags> = mutableMapOf()

    init {
        loadFromDisk()
    }

    fun getAllItems(): List<MediaItem> = applyFlags(cachedItems).filterNot { it.isHidden }

    fun getDashboard(): DashboardData {
        val items = getAllItems()
        val favorites = items.filter { it.isFavorite }
        val liveTv = items.filter { it.source == MediaSource.XTREAM_LIVE }
        val movies = items.filter {
            it.source == MediaSource.XTREAM_VOD || it.source == MediaSource.XTREAM_SERIES
        }
        val plexLibrary = items.filter { it.source == MediaSource.PLEX }
        val categories = buildCategories(liveTv)
        val categoryGroups = buildCategoryGroups(liveTv, categories)
        return DashboardData(
            favorites = favorites,
            liveTv = liveTv,
            movies = movies,
            plexLibrary = plexLibrary,
            categories = categories,
            categoryGroups = categoryGroups,
        )
    }

    private fun buildCategories(liveTv: List<MediaItem>): List<ChannelCategory> {
        return liveTv
            .mapNotNull { item ->
                val id = item.categoryId ?: return@mapNotNull null
                val name = item.categoryName ?: "Category $id"
                ChannelCategory(id, name)
            }
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    private fun buildCategoryGroups(
        liveTv: List<MediaItem>,
        categories: List<ChannelCategory>,
    ): List<CategoryGroup> {
        val uncategorized = ChannelCategory("all", "All Channels")
        val allCategories = listOf(uncategorized) + categories
        return allCategories.map { category ->
            val channels = if (category.id == "all") {
                liveTv
            } else {
                liveTv.filter { it.categoryId == category.id }
            }
            CategoryGroup(category, channels)
        }.filter { it.channels.isNotEmpty() }
    }

    fun search(query: String): SearchResult = LocalSearchEngine.search(getAllItems(), query)

    suspend fun syncCatalog(): DashboardData = withContext(Dispatchers.IO) {
        if (credentials.isDemoMode) {
            cachedItems = demoCatalog()
            saveToDisk()
            return@withContext getDashboard()
        }

        val merged = mutableListOf<MediaItem>()
        var xtreamError: String? = null
        var plexError: String? = null

        if (credentials.hasXtreamCredentials()) {
            try {
                val client = XtreamClient(
                    credentials.xtreamPortal,
                    credentials.xtreamUsername,
                    credentials.xtreamPassword,
                )
                if (!client.validateCredentials()) {
                    throw SyncException("Xtream credentials rejected by portal.")
                }
                val liveCategories = client.fetchLiveCategories()
                val vodCategories = client.fetchVodCategories()
                val liveCatMap = liveCategories.associate { it.id to it.name }
                val vodCatMap = vodCategories.associate { it.id to it.name }
                merged.addAll(client.fetchLiveStreams(liveCatMap))
                merged.addAll(client.fetchVodStreams(vodCatMap))
                merged.addAll(client.fetchSeries(vodCatMap))
            } catch (e: Exception) {
                xtreamError = e.message ?: "Xtream sync failed."
            }
        }

        if (credentials.hasPlexCredentials()) {
            try {
                val client = PlexClient(credentials.plexServerUrl, credentials.plexToken)
                if (!client.validateCredentials()) {
                    throw SyncException("Plex token or server URL is invalid.")
                }
                merged.addAll(client.fetchLibraryItems())
            } catch (e: Exception) {
                plexError = e.message ?: "Plex sync failed."
            }
        }

        if (merged.isEmpty() && (xtreamError != null || plexError != null)) {
            val parts = listOfNotNull(xtreamError, plexError)
            throw SyncException(parts.joinToString(" "))
        }

        cachedItems = merged
        saveToDisk()
        getDashboard()
    }

    fun toggleFavorite(itemId: String, favorite: Boolean) {
        val flags = itemFlags.getOrPut(itemId) { ItemFlags() }
        flags.isFavorite = favorite
        saveFlags()
    }

    fun toggleHidden(itemId: String, hidden: Boolean) {
        val flags = itemFlags.getOrPut(itemId) { ItemFlags() }
        flags.isHidden = hidden
        saveFlags()
    }

    private fun applyFlags(items: List<MediaItem>): List<MediaItem> {
        return items.map { item ->
            val flags = itemFlags[item.id]
            item.copy(
                isFavorite = flags?.isFavorite ?: item.isFavorite,
                isHidden = flags?.isHidden ?: item.isHidden,
            )
        }
    }

    private fun loadFromDisk() {
        cachedItems = readJsonList(cacheFile)
        itemFlags = readJsonMap(flagsFile)
    }

    private fun saveToDisk() {
        writeJson(cacheFile, cachedItems)
    }

    private fun saveFlags() {
        writeJson(flagsFile, itemFlags)
    }

    private fun readJsonList(file: File): List<MediaItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<MediaItem>>() {}.type
            gson.fromJson<List<MediaItem>>(file.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun readJsonMap(file: File): MutableMap<String, ItemFlags> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            val type = object : TypeToken<Map<String, ItemFlags>>() {}.type
            gson.fromJson<Map<String, ItemFlags>>(file.readText(), type)?.toMutableMap()
                ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())
    }

    private fun writeJson(file: File, data: Any) {
        file.writeText(gson.toJson(data))
    }

    private fun demoCatalog(): List<MediaItem> {
        val live = (1..12).map { index ->
            MediaItem(
                id = "demo_live_$index",
                title = "Demo Live Channel $index",
                source = MediaSource.DEMO,
                playbackId = "demo_$index",
            )
        }
        val movies = (1..10).map { index ->
            MediaItem(
                id = "demo_vod_$index",
                title = "Demo Movie $index",
                source = MediaSource.DEMO,
                playbackId = "demo_movie_$index",
            )
        }
        val plex = (1..8).map { index ->
            MediaItem(
                id = "demo_plex_$index",
                title = "Demo Plex Title $index",
                source = MediaSource.DEMO,
                playbackId = "demo_plex_$index",
            )
        }
        return live + movies + plex
    }

    data class ItemFlags(
        var isFavorite: Boolean = false,
        var isHidden: Boolean = false,
    )

    companion object {
        @Volatile
        private var instance: LocalMediaRepository? = null

        fun getInstance(context: Context): LocalMediaRepository {
            return instance ?: synchronized(this) {
                val creds = CredentialStore.getInstance(context)
                instance ?: LocalMediaRepository(context.applicationContext, creds)
                    .also { instance = it }
            }
        }
    }
}
