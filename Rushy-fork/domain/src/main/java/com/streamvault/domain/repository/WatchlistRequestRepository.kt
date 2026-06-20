package com.streamvault.domain.repository

import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series

data class WatchlistRequestOutcome(
    val success: Boolean,
    val message: String,
    val alreadyRequested: Boolean = false,
)

interface WatchlistRequestRepository {
    suspend fun requestMovie(movie: Movie): WatchlistRequestOutcome
    suspend fun requestSeries(series: Series): WatchlistRequestOutcome
}
