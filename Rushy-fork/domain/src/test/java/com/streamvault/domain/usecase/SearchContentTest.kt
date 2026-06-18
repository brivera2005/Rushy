package com.streamvault.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.manager.ProviderSyncStateReader
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.SyncState
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SearchRepository
import com.streamvault.domain.repository.SearchRepositoryResult
import com.streamvault.domain.search.NaturalLanguageSearchHints
import com.streamvault.domain.search.NaturalLanguageSearchInterpreter
import com.streamvault.domain.search.PassthroughNaturalLanguageSearchInterpreter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchContentTest {

    private val passthroughInterpreter = PassthroughNaturalLanguageSearchInterpreter()

    @Test
    fun returnsCombinedResultsAcrossAllSections() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                channelResults = listOf(Channel(id = 1L, name = "News 1")),
                movieResults = listOf(Movie(id = 2L, name = "Movie 1")),
                seriesResults = listOf(Series(id = 3L, name = "Series 1"))
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(providerId = 99L, query = "star").first()

        assertThat(result.channels).hasSize(1)
        assertThat(result.movies).hasSize(1)
        assertThat(result.series).hasSize(1)
    }

    @Test
    fun restrictsResultsToRequestedScope() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                channelResults = listOf(Channel(id = 1L, name = "News 1")),
                movieResults = listOf(Movie(id = 2L, name = "Movie 1")),
                seriesResults = listOf(Series(id = 3L, name = "Series 1"))
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(
            providerId = 99L,
            query = "star",
            scope = SearchContentScope.MOVIES
        ).first()

        assertThat(result.channels).isEmpty()
        assertThat(result.movies).hasSize(1)
        assertThat(result.series).isEmpty()
    }

    @Test
    fun shortQueriesReturnEmptyResults() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                channelResults = listOf(Channel(id = 1L, name = "News 1")),
                movieResults = listOf(Movie(id = 2L, name = "Movie 1")),
                seriesResults = listOf(Series(id = 3L, name = "Series 1"))
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(providerId = 99L, query = "a").first()

        assertThat(result.channels).isEmpty()
        assertThat(result.movies).isEmpty()
        assertThat(result.series).isEmpty()
    }

    @Test
    fun marksSearchPartialWhileProviderSyncIsActive() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(movieResults = listOf(Movie(id = 2L, name = "Movie 1"))),
            providerSyncStateReader = FakeProviderSyncStateReader(SyncState.Syncing("Indexing movies")),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(providerId = 99L, query = "movie", scope = SearchContentScope.MOVIES).first()

        assertThat(result.movies).hasSize(1)
        assertThat(result.isPartialResult).isTrue()
    }

    @Test
    fun marksSearchPartialWhileBackgroundIndexJobIsActive() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(movieResults = listOf(Movie(id = 2L, name = "Movie 1"))),
            providerSyncStateReader = FakeProviderSyncStateReader(backgroundIndexingActive = true),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(providerId = 99L, query = "movie", scope = SearchContentScope.MOVIES).first()

        assertThat(result.movies).hasSize(1)
        assertThat(result.isPartialResult).isTrue()
    }

    @Test
    fun rethrows_non_io_upstream_failures() = runTest {
        val expected = IllegalStateException("channel search failed")
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                channelFlow = flow { throw expected }
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val thrown = try {
            useCase(providerId = 99L, query = "star").first()
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown?.message).isEqualTo(expected.message)
    }

    @Test
    fun marksSearchPartialWhenRepositoryDoesNotEmitWithinBudget() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                searchContentFlow = flow {
                    delay(3_000L)
                    emit(SearchRepositoryResult(movies = listOf(Movie(id = 2L, name = "Late Movie"))))
                }
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = passthroughInterpreter
        )

        val result = useCase(providerId = 99L, query = "movie", scope = SearchContentScope.MOVIES).first()

        assertThat(result.movies).isEmpty()
        assertThat(result.isPartialResult).isTrue()
    }

    @Test
    fun usesGeminiProbableTitleBeforeOriginalQuery() = runTest {
        val useCase = SearchContent(
            searchRepository = FakeSearchRepository(
                searchContentFlow = flow {
                    emit(
                        SearchRepositoryResult(
                            movies = listOf(Movie(id = 42L, name = "The Cable Guy"))
                        )
                    )
                }
            ),
            providerRepository = FakeProviderRepository(),
            providerSyncStateReader = FakeProviderSyncStateReader(),
            naturalLanguageSearchInterpreter = FakeNaturalLanguageSearchInterpreter(
                NaturalLanguageSearchHints(
                    probableTitle = "The Cable Guy",
                    keywords = listOf("jim carrey", "matthew broderick"),
                    isConversational = true
                )
            )
        )

        val result = useCase(
            providerId = 99L,
            query = "whats that movie with jim carrey and matthew broderick",
            scope = SearchContentScope.MOVIES
        ).first()

        assertThat(result.movies).hasSize(1)
        assertThat(result.movies.first().name).isEqualTo("The Cable Guy")
    }
}

private class FakeProviderRepository : ProviderRepository {
    override fun getProviders() = flowOf(emptyList())
    override fun getActiveProvider() = flowOf(null)
    override suspend fun getProvider(id: Long) = null
    override suspend fun addProvider(provider: com.streamvault.domain.model.Provider) =
        com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun updateProvider(provider: com.streamvault.domain.model.Provider) =
        com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun deleteProvider(id: Long) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun getAllProviderCredentials() = emptyList<com.streamvault.domain.manager.ProviderCredentials>()
    override suspend fun updateProviderPassword(serverUrl: String, username: String, cleartextPassword: String) = false
    override suspend fun setActiveProvider(id: Long) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun loginXtream(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        httpUserAgent: String,
        httpHeaders: String,
        xtreamFastSyncEnabled: Boolean,
        epgSyncMode: com.streamvault.domain.model.ProviderEpgSyncMode,
        xtreamLiveSyncMode: com.streamvault.domain.model.ProviderXtreamLiveSyncMode,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun validateM3u(
        url: String,
        name: String,
        httpUserAgent: String,
        httpHeaders: String,
        epgSyncMode: com.streamvault.domain.model.ProviderEpgSyncMode,
        m3uVodClassificationEnabled: Boolean,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun loginStalker(
        portalUrl: String,
        macAddress: String,
        name: String,
        authMode: com.streamvault.domain.model.StalkerAuthMode,
        username: String,
        password: String,
        httpUserAgent: String,
        httpHeaders: String,
        deviceProfile: String,
        timezone: String,
        locale: String,
        serialNumber: String,
        deviceId: String,
        deviceId2: String,
        signature: String,
        stalkerAdvancedOptionsJson: String,
        epgSyncMode: com.streamvault.domain.model.ProviderEpgSyncMode,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun loginJellyfin(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun loginJellyfinQuickConnect(
        serverUrl: String,
        name: String,
        onCode: ((String) -> Unit)?,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun loginPlex(
        serverUrl: String,
        token: String,
        name: String,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun refreshProviderData(
        providerId: Long,
        force: Boolean,
        movieFastSyncOverride: Boolean?,
        epgSyncModeOverride: com.streamvault.domain.model.ProviderEpgSyncMode?,
        onProgress: ((String) -> Unit)?
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun getProgramsForLiveStream(
        providerId: Long,
        streamId: Long,
        epgChannelId: String?,
        limit: Int
    ) = com.streamvault.domain.model.Result.error("unsupported")
    override suspend fun buildCatchUpUrl(providerId: Long, streamId: Long, start: Long, end: Long) = null
}

private class FakeNaturalLanguageSearchInterpreter(
    private val hints: NaturalLanguageSearchHints
) : NaturalLanguageSearchInterpreter {
    override suspend fun interpret(query: String): NaturalLanguageSearchHints = hints
}

private class FakeProviderSyncStateReader(
    private val state: SyncState = SyncState.Idle,
    private val backgroundIndexingActive: Boolean = false
) : ProviderSyncStateReader {
    override fun currentSyncState(providerId: Long): SyncState = state
    override fun observeBackgroundIndexingActive(providerId: Long): Flow<Boolean> = flowOf(backgroundIndexingActive)
}

private class FakeSearchRepository(
    private val channelResults: List<Channel> = emptyList(),
    private val movieResults: List<Movie> = emptyList(),
    private val seriesResults: List<Series> = emptyList(),
    private val channelFlow: Flow<List<Channel>>? = null,
    private val movieFlow: Flow<List<Movie>>? = null,
    private val seriesFlow: Flow<List<Series>>? = null,
    private val searchContentFlow: Flow<SearchRepositoryResult>? = null
) : SearchRepository {
    override fun searchContent(
        providerId: Long,
        query: String,
        includeLive: Boolean,
        includeMovies: Boolean,
        includeSeries: Boolean,
        maxResultsPerSection: Int
    ): Flow<SearchRepositoryResult> {
        searchContentFlow?.let { return it }
        channelFlow?.let { if (includeLive) return it.map { channels -> SearchRepositoryResult(channels = channels) } }
        movieFlow?.let { if (includeMovies) return it.map { movies -> SearchRepositoryResult(movies = movies) } }
        seriesFlow?.let { if (includeSeries) return it.map { series -> SearchRepositoryResult(series = series) } }

        return flowOf(
            SearchRepositoryResult(
                channels = if (includeLive) channelResults.take(maxResultsPerSection) else emptyList(),
                movies = if (includeMovies) movieResults.take(maxResultsPerSection) else emptyList(),
                series = if (includeSeries) seriesResults.take(maxResultsPerSection) else emptyList()
            )
        )
    }

    override fun searchChannels(providerId: Long, query: String): Flow<List<Channel>> =
        channelFlow ?: flowOf(channelResults)

    override fun searchMovies(providerId: Long, query: String): Flow<List<Movie>> =
        movieFlow ?: flowOf(movieResults)

    override fun searchSeries(providerId: Long, query: String): Flow<List<Series>> =
        seriesFlow ?: flowOf(seriesResults)
}
