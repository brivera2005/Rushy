package com.streamvault.data.plex

import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.domain.model.Movie
import com.streamvault.domain.model.PlexAvailability
import com.streamvault.domain.model.Series
import com.streamvault.domain.model.hasDirectPlexMediaStream
import com.streamvault.domain.model.showsPlexBadge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexPlayabilityChecker @Inject constructor(
    private val episodeDao: EpisodeDao,
) {
    suspend fun movieAvailability(movie: Movie): PlexAvailability {
        if (!movie.showsPlexBadge()) return PlexAvailability.UNKNOWN
        movie.plexAvailability?.let { return it }
        return if (hasDirectPlexMediaStream(movie.streamUrl)) {
            PlexAvailability.PLAYABLE
        } else {
            PlexAvailability.REQUESTABLE
        }
    }

    suspend fun seriesAvailability(series: Series): PlexAvailability {
        if (!series.showsPlexBadge()) return PlexAvailability.UNKNOWN
        series.plexAvailability?.let { return it }
        series.plexHasPlayableEpisodes?.let { hasEpisodes ->
            return if (hasEpisodes) PlexAvailability.PLAYABLE else PlexAvailability.REQUESTABLE
        }
        val episodes = episodeDao.getBySeriesSync(series.id)
        val hasPlayable = episodes.any { episode -> hasDirectPlexMediaStream(episode.streamUrl) }
        return if (hasPlayable) PlexAvailability.PLAYABLE else PlexAvailability.REQUESTABLE
    }
}
