package com.streamvault.data.watchlist

import com.streamvault.data.remote.arr.ArrCredentialStore
import com.streamvault.data.remote.arr.RadarrClient
import com.streamvault.data.remote.arr.SonarrClient
import com.streamvault.data.remote.plex.PlexCredentialStore
import com.streamvault.data.remote.plex.PlexDiscoverClient
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.WatchlistRequestOutcome
import com.streamvault.domain.repository.WatchlistRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRequestRepositoryImpl @Inject constructor(
    private val radarrClient: RadarrClient,
    private val sonarrClient: SonarrClient,
    private val plexDiscoverClient: PlexDiscoverClient,
    private val arrCredentials: ArrCredentialStore,
    private val plexCredentials: PlexCredentialStore,
) : WatchlistRequestRepository {
    override suspend fun requestMovie(movie: Movie): WatchlistRequestOutcome {
        val tmdbId = movie.tmdbId
        if (arrCredentials.hasRadarr() && tmdbId != null) {
            return radarrClient.addMovie(tmdbId, movie.name).toOutcome()
        }
        return requestViaPlexDiscover(
            tmdbId = tmdbId,
            title = movie.name,
            isMovie = true,
            discoverRatingKey = movie.plexDiscoverRatingKey,
        )
    }

    override suspend fun requestSeries(series: Series): WatchlistRequestOutcome {
        val tmdbId = series.tmdbId
        if (arrCredentials.hasSonarr() && tmdbId != null) {
            return sonarrClient.addSeries(tmdbId, series.name).toOutcome()
        }
        return requestViaPlexDiscover(
            tmdbId = tmdbId,
            title = series.name,
            isMovie = false,
            discoverRatingKey = series.plexDiscoverRatingKey,
        )
    }

    private fun requestViaPlexDiscover(
        tmdbId: Long?,
        title: String,
        isMovie: Boolean,
        discoverRatingKey: String?,
    ): WatchlistRequestOutcome {
        if (!plexCredentials.hasCredentials()) {
            return if (arrCredentials.hasAnyArr()) {
                WatchlistRequestOutcome(
                    success = false,
                    message = if (isMovie) {
                        "Configure Radarr in Settings to request movies"
                    } else {
                        "Configure Sonarr in Settings to request series"
                    },
                )
            } else {
                WatchlistRequestOutcome(
                    success = false,
                    message = "Configure Radarr/Sonarr or Plex in Settings to request content",
                )
            }
        }
        val ratingKey = discoverRatingKey
            ?: tmdbId?.let { plexDiscoverClient.resolveDiscoverRatingKey(it, isMovie) }
        if (ratingKey.isNullOrBlank()) {
            return WatchlistRequestOutcome(
                success = false,
                message = "Could not resolve Plex metadata for $title",
            )
        }
        return plexDiscoverClient.addToWatchlist(ratingKey, title).toOutcome()
    }

    private fun com.streamvault.data.remote.arr.WatchlistRequestResult.toOutcome() =
        WatchlistRequestOutcome(
            success = success,
            message = message,
            alreadyRequested = alreadyRequested,
        )
}
