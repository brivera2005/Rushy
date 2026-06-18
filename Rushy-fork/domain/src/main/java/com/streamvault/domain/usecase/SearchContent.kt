package com.streamvault.domain.usecase

import com.streamvault.domain.manager.ProviderSyncStateReader
import com.streamvault.domain.model.CatalogSource
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.SyncState
import com.streamvault.domain.model.toCatalogSource
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.SearchRepository
import com.streamvault.domain.repository.SearchRepositoryResult
import com.streamvault.domain.search.NaturalLanguageSearchInterpreter
import com.streamvault.domain.search.NaturalLanguageSearchQueryBuilder
import com.streamvault.domain.util.shouldRethrowDomainFlowFailure
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

enum class SearchContentScope {
    ALL,
    LIVE,
    MOVIES,
    SERIES
}

data class SearchContentResult(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val isPartialResult: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchContent @Inject constructor(
    private val searchRepository: SearchRepository,
    private val providerRepository: ProviderRepository,
    private val providerSyncStateReader: ProviderSyncStateReader,
    private val naturalLanguageSearchInterpreter: NaturalLanguageSearchInterpreter
) {
    private companion object {
        const val SEARCH_RESPONSE_TIMEOUT_MS = 2_500L
        const val LOW_CONFIDENCE_RESULT_THRESHOLD = 3
        val XTREAM_PROVIDER_TYPES = setOf(
            ProviderType.XTREAM_CODES,
            ProviderType.M3U,
            ProviderType.STALKER_PORTAL
        )
    }

    private val logger = Logger.getLogger("SearchContent")

    operator fun invoke(
        providerId: Long,
        query: String,
        scope: SearchContentScope = SearchContentScope.ALL,
        maxResultsPerSection: Int = 120
    ): Flow<SearchContentResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) {
            return flowOf(SearchContentResult())
        }

        return flow {
            val (xtreamProviderId, plexProviderId) = resolveSearchProviders(providerId)
            val primaryProviderId = xtreamProviderId ?: providerId
            val fallbackProviderId = plexProviderId?.takeIf { it != primaryProviderId }
            emit(primaryProviderId to fallbackProviderId)
        }.flatMapLatest { (primaryProviderId, fallbackProviderId) ->
            combine(
                contentSearchFlow(
                    primaryProviderId = primaryProviderId,
                    fallbackProviderId = fallbackProviderId,
                    query = normalizedQuery,
                    scope = scope,
                    maxResultsPerSection = maxResultsPerSection
                ),
                providerSyncStateReader.observeBackgroundIndexingActive(primaryProviderId),
                fallbackProviderId?.let { providerSyncStateReader.observeBackgroundIndexingActive(it) }
                    ?: flowOf(false)
            ) { searchResult, primaryIndexActive, fallbackIndexActive ->
                val (result, searchDegraded) = searchResult
                val providerIds = listOfNotNull(primaryProviderId, fallbackProviderId)
                val indexingActive = providerIds.any { id ->
                    when (providerSyncStateReader.currentSyncState(id)) {
                        is SyncState.Syncing,
                        is SyncState.Partial -> true
                        else -> false
                    }
                }
                SearchContentResult(
                    channels = result.channels,
                    movies = result.movies,
                    series = result.series,
                    isPartialResult = searchDegraded || indexingActive || primaryIndexActive || fallbackIndexActive
                )
            }
        }
    }

    private suspend fun resolveSearchProviders(primaryProviderId: Long): Pair<Long?, Long?> {
        val providers = providerRepository.getProviders().first()
        val primary = providers.find { it.id == primaryProviderId }
        val xtreamId = when {
            primary?.type in XTREAM_PROVIDER_TYPES -> primary?.id
            else -> providers.firstOrNull { it.type in XTREAM_PROVIDER_TYPES }?.id
        }
        val plexId = providers.firstOrNull { it.type == ProviderType.PLEX }?.id
        return xtreamId to plexId
    }

    private fun contentSearchFlow(
        primaryProviderId: Long,
        fallbackProviderId: Long?,
        query: String,
        scope: SearchContentScope,
        maxResultsPerSection: Int
    ): Flow<Pair<SearchRepositoryResult, Boolean>> =
        flow {
            val hints = naturalLanguageSearchInterpreter.interpret(query)
            val searchQueries = NaturalLanguageSearchQueryBuilder.buildQueries(query, hints)

            var merged = SearchRepositoryResult()
            var degraded = false

            for (searchQuery in searchQueries) {
                val primaryResult = executeSearch(
                    providerId = primaryProviderId,
                    query = searchQuery,
                    scope = scope,
                    maxResultsPerSection = maxResultsPerSection
                )
                degraded = degraded || primaryResult.second

                var combined = tagCatalogSource(primaryResult.first, CatalogSource.XTREAM)
                if (fallbackProviderId != null && shouldQueryFallbackProvider(combined, scope)) {
                    val fallbackResult = executeSearch(
                        providerId = fallbackProviderId,
                        query = searchQuery,
                        scope = scope,
                        maxResultsPerSection = maxResultsPerSection
                    )
                    degraded = degraded || fallbackResult.second
                    combined = mergeXtreamFirst(
                        combined,
                        tagCatalogSource(fallbackResult.first, CatalogSource.PLEX),
                        scope,
                        maxResultsPerSection
                    )
                }

                merged = mergeSearchResults(merged, combined, maxResultsPerSection)

                if (hints.probableTitle.isNotBlank() &&
                    searchQuery.equals(hints.probableTitle, ignoreCase = true) &&
                    merged.hasAnyResults()
                ) {
                    break
                }
            }

            emit(merged to degraded)
        }
            .catch { error ->
                if (error.shouldRethrowDomainFlowFailure()) throw error
                logger.log(Level.WARNING, "Unified content search failed", error)
                emit(SearchRepositoryResult() to true)
            }

    private suspend fun executeSearch(
        providerId: Long,
        query: String,
        scope: SearchContentScope,
        maxResultsPerSection: Int
    ): Pair<SearchRepositoryResult, Boolean> {
        val result = withTimeoutOrNull(SEARCH_RESPONSE_TIMEOUT_MS) {
            searchRepository.searchContent(
                providerId = providerId,
                query = query,
                includeLive = scope == SearchContentScope.ALL || scope == SearchContentScope.LIVE,
                includeMovies = scope == SearchContentScope.ALL || scope == SearchContentScope.MOVIES,
                includeSeries = scope == SearchContentScope.ALL || scope == SearchContentScope.SERIES,
                maxResultsPerSection = maxResultsPerSection
            ).first()
        }

        return if (result == null) {
            logger.warning("Search timed out for provider $providerId and query '$query'")
            SearchRepositoryResult() to true
        } else {
            result to false
        }
    }

    private fun shouldQueryFallbackProvider(
        result: SearchRepositoryResult,
        scope: SearchContentScope
    ): Boolean = when (scope) {
        SearchContentScope.ALL ->
            result.movies.size < LOW_CONFIDENCE_RESULT_THRESHOLD ||
                result.series.size < LOW_CONFIDENCE_RESULT_THRESHOLD
        SearchContentScope.LIVE -> result.channels.size < LOW_CONFIDENCE_RESULT_THRESHOLD
        SearchContentScope.MOVIES -> result.movies.size < LOW_CONFIDENCE_RESULT_THRESHOLD
        SearchContentScope.SERIES -> result.series.size < LOW_CONFIDENCE_RESULT_THRESHOLD
    }

    private fun isLowConfidence(result: SearchRepositoryResult, scope: SearchContentScope): Boolean =
        shouldQueryFallbackProvider(result, scope)

    private fun mergeXtreamFirst(
        primary: SearchRepositoryResult,
        fallback: SearchRepositoryResult,
        scope: SearchContentScope,
        maxResultsPerSection: Int
    ): SearchRepositoryResult {
        val mergedMovies = when (scope) {
            SearchContentScope.LIVE -> primary.movies
            else -> (primary.movies + fallback.movies).distinctBy { it.id }.take(maxResultsPerSection)
        }
        val mergedSeries = when (scope) {
            SearchContentScope.LIVE, SearchContentScope.MOVIES -> primary.series
            else -> (primary.series + fallback.series).distinctBy { it.id }.take(maxResultsPerSection)
        }
        val mergedChannels = when (scope) {
            SearchContentScope.MOVIES, SearchContentScope.SERIES -> primary.channels
            else -> (primary.channels + fallback.channels).distinctBy { it.id }.take(maxResultsPerSection)
        }
        return SearchRepositoryResult(
            channels = mergedChannels,
            movies = mergedMovies,
            series = mergedSeries
        )
    }

    private fun tagCatalogSource(
        result: SearchRepositoryResult,
        source: CatalogSource
    ): SearchRepositoryResult = SearchRepositoryResult(
        channels = result.channels.map { it.copy(catalogSource = source) },
        movies = result.movies.map { it.copy(catalogSource = source) },
        series = result.series.map { it.copy(catalogSource = source) }
    )

    private fun mergeSearchResults(
        current: SearchRepositoryResult,
        incoming: SearchRepositoryResult,
        maxResultsPerSection: Int
    ): SearchRepositoryResult =
        SearchRepositoryResult(
            channels = (current.channels + incoming.channels).distinctBy { it.id }.take(maxResultsPerSection),
            movies = (current.movies + incoming.movies).distinctBy { it.id }.take(maxResultsPerSection),
            series = (current.series + incoming.series).distinctBy { it.id }.take(maxResultsPerSection)
        )

    private fun SearchRepositoryResult.hasAnyResults(): Boolean =
        channels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()
}
