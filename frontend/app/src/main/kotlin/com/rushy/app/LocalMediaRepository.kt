package com.rushy.app



import android.content.Context

import android.util.Log

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import java.io.File



class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)



class LocalMediaRepository private constructor(

    private val context: Context,

    private val credentials: CredentialStore,

) {

    private val dao by lazy { MediaDatabase.getInstance(context).mediaDao() }

    private val legacyCacheFile = File(context.filesDir, "media_cache.json")

    private val legacyFlagsFile = File(context.filesDir, "media_flags.json")



    init {

        runCatching { legacyCacheFile.delete() }

        runCatching { legacyFlagsFile.delete() }

    }



    suspend fun verifyDatabaseHealth(): Boolean = withContext(Dispatchers.IO) {

        try {

            dao.totalCount()

            true

        } catch (e: Exception) {

            Log.e(TAG, "Database health check failed", e)

            false

        }

    }



    suspend fun getSummary(): CatalogSummary = withContext(Dispatchers.IO) {

        buildSummary()

    }



    suspend fun getLiveChannels(

        categoryId: String = "all",

        search: String = "",

        offset: Int = 0,

        limit: Int = PAGE_SIZE,

    ): List<MediaItem> = withContext(Dispatchers.IO) {

        dao.getItemsPaged(

            MediaSource.XTREAM_LIVE.name,

            categoryId,

            search.trim(),

            limit,

            offset,

        ).map { it.toMediaItem() }

    }



    suspend fun getVodItems(

        categoryId: String = "all",

        search: String = "",

        offset: Int = 0,

        limit: Int = PAGE_SIZE,

    ): List<MediaItem> = withContext(Dispatchers.IO) {

        val vod = dao.getItemsPaged(MediaSource.XTREAM_VOD.name, categoryId, search.trim(), limit, offset)

        val series = if (offset == 0 && categoryId == "all" && search.isBlank()) {

            dao.getItemsPaged(MediaSource.XTREAM_SERIES.name, categoryId, search.trim(), limit, 0)

        } else {

            emptyList()

        }

        (vod + series).map { it.toMediaItem() }

    }



    suspend fun getItemsBySource(

        source: MediaSource,

        categoryId: String = "all",

        search: String = "",

        offset: Int = 0,

        limit: Int = PAGE_SIZE,

    ): List<MediaItem> = withContext(Dispatchers.IO) {

        dao.getItemsPaged(source.name, categoryId, search.trim(), limit, offset).map { it.toMediaItem() }

    }



    suspend fun countInCategory(source: MediaSource, categoryId: String, search: String = ""): Int =

        withContext(Dispatchers.IO) {

            dao.countInCategory(source.name, categoryId, search.trim())

        }



    suspend fun getFavorites(limit: Int = 24): List<MediaItem> = withContext(Dispatchers.IO) {

        dao.getFavorites(limit).map { it.toMediaItem() }

    }



    suspend fun getFeaturedLive(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {

        dao.getRandomFeatured(MediaSource.XTREAM_LIVE.name, limit).map { it.toMediaItem() }

    }



    suspend fun search(query: String, limit: Int = 80): SearchResult = withContext(Dispatchers.IO) {

        val normalized = query.trim()

        if (normalized.isBlank()) return@withContext SearchResult()

        val sqlResults = dao.searchAll(normalized, limit).map { it.toMediaItem() }

        LocalSearchEngine.search(sqlResults, normalized)

    }



    private suspend fun notifyProgress(
        onProgress: (SyncProgress) -> Unit,
        progress: SyncProgress,
    ) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(progress)
        }
    }

    suspend fun syncCatalog(onProgress: (SyncProgress) -> Unit = {}): CatalogSummary =

        withContext(Dispatchers.IO) {

            try {

                if (credentials.isDemoMode) {

                    notifyProgress(onProgress, SyncProgress("Loading demo catalog..."))

                    seedDemoCatalog()

                    return@withContext buildSummary()

                }



                var xtreamError: String? = null

                var plexError: String? = null

                var totalInserted = 0



                if (credentials.hasXtreamCredentials()) {

                    try {

                        notifyProgress(onProgress, SyncProgress("Validating credentials..."))

                        val client = XtreamClient(

                            credentials.xtreamPortal,

                            credentials.xtreamUsername,

                            credentials.xtreamPassword,

                        )

                        if (!client.validateCredentials()) {

                            throw SyncException("Xtream credentials rejected by portal.")

                        }



                        dao.deleteBySources(

                            listOf(

                                MediaSource.XTREAM_LIVE.name,

                                MediaSource.XTREAM_VOD.name,

                                MediaSource.XTREAM_SERIES.name,

                            ),

                        )



                        notifyProgress(onProgress, SyncProgress("Loading Live TV categories..."))

                        val liveCategories = client.fetchLiveCategories()



                        notifyProgress(onProgress, SyncProgress("Downloading Live TV channels..."))

                        var liveSaved = 0

                        client.fetchLiveStreamsByCategory(liveCategories) { categoryName, items ->

                            liveSaved += insertInBatches(items) { count ->

                                notifyProgress(

                                    onProgress,

                                    SyncProgress("Saving Live TV: $categoryName...", liveSaved + count),

                                )

                            }

                        }

                        totalInserted += liveSaved



                        notifyProgress(onProgress, SyncProgress("Loading movie categories..."))

                        val vodCategories = client.fetchVodCategories()

                        val vodCatMap = vodCategories.associate { it.id to it.name }



                        notifyProgress(onProgress, SyncProgress("Downloading movies..."))

                        var vodSaved = 0

                        client.fetchVodStreamsByCategory(vodCategories) { categoryName, items ->

                            vodSaved += insertInBatches(items) { count ->

                                notifyProgress(

                                    onProgress,

                                    SyncProgress("Saving movies: $categoryName...", vodSaved + count),

                                )

                            }

                        }

                        totalInserted += vodSaved



                        notifyProgress(onProgress, SyncProgress("Downloading series..."))

                        val series = client.fetchSeries(vodCatMap)

                        totalInserted += insertInBatches(series) { count ->

                            notifyProgress(onProgress, SyncProgress("Saving series...", count))

                        }



                        Log.i(TAG, "Xtream sync complete: $totalInserted items")

                    } catch (e: Exception) {

                        Log.e(TAG, "Xtream sync failed", e)

                        xtreamError = e.message ?: "Xtream sync failed."

                    }

                }



                if (credentials.hasPlexCredentials()) {

                    try {

                        notifyProgress(onProgress, SyncProgress("Syncing Plex library..."))

                        val client = PlexClient(credentials.plexServerUrl, credentials.plexToken)

                        if (!client.validateCredentials()) {

                            throw SyncException("Plex token or server URL is invalid.")

                        }

                        dao.deleteBySources(listOf(MediaSource.PLEX.name))

                        val plexItems = client.fetchLibraryItems()

                        insertInBatches(plexItems) { count ->

                            notifyProgress(onProgress, SyncProgress("Saving Plex items...", count))

                        }

                    } catch (e: Exception) {

                        Log.e(TAG, "Plex sync failed", e)

                        plexError = e.message ?: "Plex sync failed."

                    }

                }



                val summary = buildSummary()

                if (summary.liveCount + summary.movieCount + summary.plexCount == 0 &&

                    (xtreamError != null || plexError != null)

                ) {

                    throw SyncException(listOfNotNull(xtreamError, plexError).joinToString(" "))

                }



                notifyProgress(
                    onProgress,
                    SyncProgress("Ready!", summary.liveCount + summary.movieCount + summary.plexCount),
                )

                summary

            } catch (e: Exception) {

                Log.e(TAG, "Catalog sync failed", e)

                throw if (e is SyncException) e else SyncException(e.message ?: "Sync failed.", e)

            }

        }



    suspend fun toggleFavorite(itemId: String, favorite: Boolean) = withContext(Dispatchers.IO) {

        dao.setFavorite(itemId, favorite)

    }



    suspend fun toggleHidden(itemId: String, hidden: Boolean) = withContext(Dispatchers.IO) {

        dao.setHidden(itemId, hidden)

    }



    private suspend fun insertInBatches(

        items: List<MediaItem>,

        onBatch: suspend (Int) -> Unit,

    ): Int {

        var total = 0

        items.chunked(BATCH_SIZE).forEach { chunk ->

            dao.insertAll(chunk.map { it.toEntity() })

            total += chunk.size

            onBatch(total)

        }

        return total

    }



    private suspend fun seedDemoCatalog() {

        dao.deleteBySources(

            listOf(

                MediaSource.DEMO.name,

                MediaSource.XTREAM_LIVE.name,

                MediaSource.XTREAM_VOD.name,

                MediaSource.XTREAM_SERIES.name,

                MediaSource.PLEX.name,

            ),

        )

        insertInBatches(demoCatalog()) {}

    }



    private suspend fun buildSummary(): CatalogSummary {

        val liveCategories = dao.getCategories(MediaSource.XTREAM_LIVE.name).map {

            ChannelCategory(it.categoryId, it.categoryName ?: "Category ${it.categoryId}")

        }

        val vodCategories = dao.getCategories(MediaSource.XTREAM_VOD.name).map {

            ChannelCategory(it.categoryId, it.categoryName ?: "Category ${it.categoryId}")

        } + dao.getCategories(MediaSource.XTREAM_SERIES.name).map {

            ChannelCategory(it.categoryId, it.categoryName ?: "Series ${it.categoryId}")

        }.distinctBy { it.id }



        return CatalogSummary(

            liveCount = dao.countBySource(MediaSource.XTREAM_LIVE.name),

            vodCount = dao.countBySource(MediaSource.XTREAM_VOD.name),

            seriesCount = dao.countBySource(MediaSource.XTREAM_SERIES.name),

            plexCount = dao.countBySource(MediaSource.PLEX.name),

            favoriteCount = dao.countFavorites(),

            liveCategories = listOf(ChannelCategory("all", "All Channels")) + liveCategories,

            vodCategories = listOf(ChannelCategory("all", "All")) + vodCategories.distinctBy { it.id },

        )

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



    companion object {

        private const val TAG = "LocalMediaRepository"

        private const val BATCH_SIZE = 400

        const val PAGE_SIZE = 60



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


