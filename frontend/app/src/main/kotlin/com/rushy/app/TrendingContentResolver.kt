package com.rushy.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrendingActionState(
    val playableItem: MediaItem? = null,
    val isOnWatchlist: Boolean = false,
    val isResolving: Boolean = false,
)

class TrendingContentResolver(
    private val context: Context,
    private val credentials: CredentialStore,
) {
    private val plexClient get() = PlexWatchlistClient.fromCredentials(credentials)
    private var watchlistCache: Set<Int>? = null
    private var watchlistCacheTime: Long = 0L

    suspend fun refreshWatchlistCache(): Set<Int> = withContext(Dispatchers.IO) {
        if (!credentials.hasPlexCredentials()) {
            watchlistCache = emptySet()
            return@withContext emptySet()
        }
        val ids = plexClient.fetchWatchlistTmdbIds()
        watchlistCache = ids
        watchlistCacheTime = System.currentTimeMillis()
        ids
    }

    private suspend fun getWatchlistIds(): Set<Int> {
        if (watchlistCache != null && System.currentTimeMillis() - watchlistCacheTime < WATCHLIST_CACHE_MS) {
            return watchlistCache!!
        }
        return refreshWatchlistCache()
    }

    suspend fun resolveAction(
        item: TmdbMediaItem,
        repository: LocalMediaRepository,
    ): TrendingActionState = withContext(Dispatchers.IO) {
        val vodMatch = TrendingRepository.getInstance(context).matchToCatalog(item, repository)
        val plexRoomMatch = if (vodMatch == null && credentials.hasPlexCredentials()) {
            searchPlexInRoom(item, repository)
        } else {
            null
        }
        val plexLiveMatch = if (vodMatch == null && plexRoomMatch == null && credentials.hasPlexCredentials()) {
            plexClient.searchServerLibrary(item.displayTitle, item.isMovie)
        } else {
            null
        }
        val playable = vodMatch ?: plexRoomMatch ?: plexLiveMatch
        val onWatchlist = if (credentials.hasPlexCredentials()) {
            plexClient.isOnWatchlist(item.id, item.isMovie, getWatchlistIds())
        } else {
            false
        }
        TrendingActionState(
            playableItem = playable,
            isOnWatchlist = onWatchlist,
        )
    }

    suspend fun requestOnPlex(item: TmdbMediaItem): PlexWatchlistResult {
        val result = plexClient.addToWatchlist(item)
        if (result.success) {
            watchlistCache = (watchlistCache ?: emptySet()) + item.id
        }
        return result
    }

    suspend fun testPlexConnection(): Boolean = plexClient.testConnection()

    private suspend fun searchPlexInRoom(
        item: TmdbMediaItem,
        repository: LocalMediaRepository,
    ): MediaItem? {
        val title = item.displayTitle.lowercase()
        val plexItems = repository.getItemsBySource(MediaSource.PLEX, limit = 500)
        return plexItems.firstOrNull { media ->
            val mediaTitle = media.title.lowercase()
            mediaTitle == title || mediaTitle.contains(title) || title.contains(mediaTitle)
        }
    }

    companion object {
        private const val WATCHLIST_CACHE_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: TrendingContentResolver? = null

        fun getInstance(context: Context): TrendingContentResolver {
            return instance ?: synchronized(this) {
                instance ?: TrendingContentResolver(
                    context.applicationContext,
                    CredentialStore.getInstance(context),
                ).also { instance = it }
            }
        }
    }
}
