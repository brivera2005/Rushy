package com.streamvault.domain.usecase

import com.streamvault.domain.model.Movie
import com.streamvault.domain.repository.MovieRepository
import com.streamvault.domain.util.shouldRethrowDomainFlowFailure
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed class RecommendationsResult {
    data class Success(val movies: List<Movie>) : RecommendationsResult()
    data object Degraded : RecommendationsResult()
}

class GetRecommendations @Inject constructor(
    private val movieRepository: MovieRepository
) {
    private val logger = Logger.getLogger("GetRecommendations")

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(providerId: Long, limit: Int = 12): Flow<RecommendationsResult> {
        return movieRepository.getRecommendations(providerId, limit)
            .flatMapLatest { recommended ->
                if (recommended.isNotEmpty()) {
                    flowOf(RecommendationsResult.Success(recommended.take(limit)) as RecommendationsResult)
                } else {
                    movieRepository.getTopRatedPreview(providerId, limit)
                        .map { fallback -> RecommendationsResult.Success(fallback.take(limit)) }
                }
            }
            .catch { error ->
                if (error.shouldRethrowDomainFlowFailure()) {
                    throw error
                }
                logger.log(Level.WARNING, "Failed to load recommendations", error)
                emit(RecommendationsResult.Degraded)
            }
    }
}