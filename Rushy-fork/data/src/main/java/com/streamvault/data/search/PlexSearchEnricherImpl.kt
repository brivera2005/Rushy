package com.streamvault.data.search

import com.streamvault.data.plex.PlexPlayabilityChecker
import com.streamvault.data.remote.plex.PlexDiscoverClient
import com.streamvault.data.remote.plex.PlexDiscoverHit
import com.streamvault.domain.model.CatalogSource
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.PlexAvailability
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.showsPlexBadge
import com.streamvault.domain.repository.PlexSearchEnricher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexSearchEnricherImpl @Inject constructor(
    private val playabilityChecker: PlexPlayabilityChecker,
    private val plexDiscoverClient: PlexDiscoverClient,
) : PlexSearchEnricher {
    override suspend fun enrich(
        movies: List<Movie>,
        series: List<Series>,
        query: String,
        includeMovies: Boolean,
        includeSeries: Boolean,
        maxResultsPerSection: Int,
        usedPlexFallback: Boolean,
    ): Pair<List<Movie>, List<Series>> {
        val enrichedMovies = enrichMovies(movies)
        val enrichedSeries = enrichSeries(series)

        if (!usedPlexFallback) {
            return enrichedMovies to enrichedSeries
        }

        val discoverMovies = if (includeMovies) {
            mergeDiscoverMovies(
                current = enrichedMovies,
                query = query,
                maxResults = maxResultsPerSection,
            )
        } else {
            enrichedMovies
        }

        val discoverSeries = if (includeSeries) {
            mergeDiscoverSeries(
                current = enrichedSeries,
                query = query,
                maxResults = maxResultsPerSection,
            )
        } else {
            enrichedSeries
        }

        return discoverMovies to discoverSeries
    }

    private suspend fun enrichMovies(movies: List<Movie>): List<Movie> =
        movies.map { movie ->
            if (!movie.showsPlexBadge()) return@map movie
            val availability = playabilityChecker.movieAvailability(movie)
            movie.copy(plexAvailability = availability)
        }

    private suspend fun enrichSeries(seriesList: List<Series>): List<Series> =
        seriesList.map { series ->
            if (!series.showsPlexBadge()) return@map series
            val availability = playabilityChecker.seriesAvailability(series)
            val hasEpisodes = when (availability) {
                PlexAvailability.PLAYABLE -> true
                PlexAvailability.REQUESTABLE -> false
                PlexAvailability.UNKNOWN -> null
            }
            series.copy(
                plexAvailability = availability,
                plexHasPlayableEpisodes = hasEpisodes,
            )
        }

    private fun mergeDiscoverMovies(
        current: List<Movie>,
        query: String,
        maxResults: Int,
    ): List<Movie> {
        if (current.count { it.catalogSource == CatalogSource.PLEX } >= maxResults) return current
        val existingKeys = current.map { it.name.lowercase() }.toMutableSet()
        val existingTmdb = current.mapNotNull { it.tmdbId }.toMutableSet()
        val discoverHits = plexDiscoverClient.searchDiscover(query, includeMovies = true, includeSeries = false)
            .filter { it.type == "movie" }
        val additions = discoverHits.mapNotNull { hit -> hit.toDiscoverMovie(existingKeys, existingTmdb) }
        if (additions.isEmpty()) return current
        return (current + additions).distinctBy { it.tmdbId ?: it.id }.take(maxResults)
    }

    private fun mergeDiscoverSeries(
        current: List<Series>,
        query: String,
        maxResults: Int,
    ): List<Series> {
        if (current.count { it.catalogSource == CatalogSource.PLEX } >= maxResults) return current
        val existingKeys = current.map { it.name.lowercase() }.toMutableSet()
        val existingTmdb = current.mapNotNull { it.tmdbId }.toMutableSet()
        val discoverHits = plexDiscoverClient.searchDiscover(query, includeMovies = false, includeSeries = true)
            .filter { it.type == "show" }
        val additions = discoverHits.mapNotNull { hit -> hit.toDiscoverSeries(existingKeys, existingTmdb) }
        if (additions.isEmpty()) return current
        return (current + additions).distinctBy { it.tmdbId ?: it.id }.take(maxResults)
    }

    private fun PlexDiscoverHit.toDiscoverMovie(
        existingKeys: MutableSet<String>,
        existingTmdb: MutableSet<Long>,
    ): Movie? {
        val normalizedTitle = title.lowercase()
        if (normalizedTitle in existingKeys) return null
        tmdbId?.let { if (it in existingTmdb) return null }
        existingKeys += normalizedTitle
        tmdbId?.let { existingTmdb += it }
        val syntheticId = syntheticDiscoverId(tmdbId ?: stableHashId(ratingKey))
        return Movie(
            id = syntheticId,
            name = title,
            posterUrl = posterUrl,
            categoryName = "Plex Discover",
            plot = summary,
            year = year,
            tmdbId = tmdbId,
            catalogSource = CatalogSource.PLEX,
            plexAvailability = PlexAvailability.REQUESTABLE,
            plexDiscoverRatingKey = ratingKey,
        )
    }

    private fun PlexDiscoverHit.toDiscoverSeries(
        existingKeys: MutableSet<String>,
        existingTmdb: MutableSet<Long>,
    ): Series? {
        val normalizedTitle = title.lowercase()
        if (normalizedTitle in existingKeys) return null
        tmdbId?.let { if (it in existingTmdb) return null }
        existingKeys += normalizedTitle
        tmdbId?.let { existingTmdb += it }
        val syntheticId = syntheticDiscoverId(tmdbId ?: stableHashId(ratingKey))
        return Series(
            id = syntheticId,
            name = title,
            posterUrl = posterUrl,
            categoryName = "Plex Discover",
            plot = summary,
            releaseDate = year,
            tmdbId = tmdbId,
            catalogSource = CatalogSource.PLEX,
            plexAvailability = PlexAvailability.REQUESTABLE,
            plexDiscoverRatingKey = ratingKey,
            plexHasPlayableEpisodes = false,
        )
    }

    private fun syntheticDiscoverId(seed: Long): Long = -1L * (seed and Long.MAX_VALUE)

    private fun stableHashId(value: String): Long {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        var result = 0L
        for (i in 0 until 8) result = (result shl 8) or (digest[i].toLong() and 0xff)
        return result and Long.MAX_VALUE
    }
}
