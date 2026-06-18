package com.streamvault.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.EpisodeDao
import com.streamvault.data.local.dao.PlaybackHistoryDao
import com.streamvault.data.local.entity.EpisodeEntity
import com.streamvault.data.local.entity.PlaybackHistoryEntity
import com.streamvault.domain.model.ContentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class EpisodeDaoTest {
    private lateinit var db: StreamVaultDatabase
    private lateinit var episodeDao: EpisodeDao
    private lateinit var historyDao: PlaybackHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, StreamVaultDatabase::class.java
        ).build()
        episodeDao = db.episodeDao()
        historyDao = db.playbackHistoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun syncWatchProgressFromHistory_updatesEpisodeFromPlaybackHistory() = runTest {
        episodeDao.insertAll(
            listOf(
                EpisodeEntity(
                    id = 11L,
                    episodeId = 201L,
                    title = "Episode 1",
                    episodeNumber = 1,
                    seasonNumber = 1,
                    seriesId = 91L,
                    providerId = 5L,
                    watchProgress = 0L
                )
            )
        )
        historyDao.insertOrUpdate(
            PlaybackHistoryEntity(
                contentId = 11L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 5L,
                resumePositionMs = 18_000L,
                lastWatchedAt = 27_000L
            )
        )

        episodeDao.syncWatchProgressFromHistory(11L, 5L)

        val episode = episodeDao.getById(11L)
        assertThat(episode).isNotNull()
        assertThat(episode?.watchProgress).isEqualTo(18_000L)
        assertThat(episode?.lastWatchedAt).isEqualTo(27_000L)
    }

    @Test
    fun syncWatchProgressFromHistoryByProvider_updatesMatchingEpisodesAndClearsStaleRows() = runTest {
        episodeDao.insertAll(
            listOf(
                EpisodeEntity(
                    id = 11L,
                    episodeId = 201L,
                    title = "Episode 1",
                    episodeNumber = 1,
                    seasonNumber = 1,
                    seriesId = 91L,
                    providerId = 5L,
                    watchProgress = 0L,
                    lastWatchedAt = 0L
                ),
                EpisodeEntity(
                    id = 12L,
                    episodeId = 202L,
                    title = "Episode 2",
                    episodeNumber = 2,
                    seasonNumber = 1,
                    seriesId = 91L,
                    providerId = 5L,
                    watchProgress = 13_000L,
                    lastWatchedAt = 14_000L
                ),
                EpisodeEntity(
                    id = 21L,
                    episodeId = 301L,
                    title = "Other Provider Episode",
                    episodeNumber = 1,
                    seasonNumber = 1,
                    seriesId = 101L,
                    providerId = 6L,
                    watchProgress = 3_000L,
                    lastWatchedAt = 4_000L
                )
            )
        )
        historyDao.insertOrUpdate(
            PlaybackHistoryEntity(
                contentId = 11L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 5L,
                resumePositionMs = 18_000L,
                lastWatchedAt = 27_000L
            )
        )

        episodeDao.syncWatchProgressFromHistoryByProvider(5L)

        val watchedEpisode = episodeDao.getById(11L)
        val staleEpisode = episodeDao.getById(12L)
        val otherProviderEpisode = episodeDao.getById(21L)

        assertThat(watchedEpisode?.watchProgress).isEqualTo(18_000L)
        assertThat(watchedEpisode?.lastWatchedAt).isEqualTo(27_000L)
        assertThat(staleEpisode?.watchProgress).isEqualTo(0L)
        assertThat(staleEpisode?.lastWatchedAt).isEqualTo(0L)
        assertThat(otherProviderEpisode?.watchProgress).isEqualTo(3_000L)
        assertThat(otherProviderEpisode?.lastWatchedAt).isEqualTo(4_000L)
    }
}