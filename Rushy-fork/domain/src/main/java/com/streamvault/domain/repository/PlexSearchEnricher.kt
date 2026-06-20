package com.streamvault.domain.repository

import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series

interface PlexSearchEnricher {
    suspend fun enrich(
        movies: List<Movie>,
        series: List<Series>,
        query: String,
        includeMovies: Boolean,
        includeSeries: Boolean,
        maxResultsPerSection: Int,
        usedPlexFallback: Boolean,
    ): Pair<List<Movie>, List<Series>>
}
