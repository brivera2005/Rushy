package com.streamvault.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streamvault.data.local.dao.MovieDao
import com.streamvault.data.local.dao.PlaybackHistoryDao
import com.streamvault.data.local.entity.MovieEntity
import com.streamvault.data.local.entity.PlaybackHistoryEntity
import com.streamvault.domain.model.ContentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MovieDaoTest {
    private lateinit var db: StreamVaultDatabase
    private lateinit var movieDao: MovieDao
    private lateinit var historyDao: PlaybackHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, StreamVaultDatabase::class.java
        ).build()
        movieDao = db.movieDao()
        historyDao = db.playbackHistoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRestoreWatchProgress() = runTest {
        // 1. Insert history for a movie. Use contentId = 1L
        val history = PlaybackHistoryEntity(
            contentId = 1L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            resumePositionMs = 5000L,
            lastWatchedAt = 10000L
        )
        historyDao.insertOrUpdate(history)

        // 2. Replace movies in provider 1. Give it ID 1L
        val movie = MovieEntity(
            id = 1L, // MUST MATCH contentId in history
            name = "Test Movie",
            providerId = 1L,
            watchProgress = 0L // default
        )
        movieDao.replaceAll(1L, listOf(movie))

        // 3. Verify watch progress was restored from history during replaceAll
        val restoredMovie = movieDao.getById(1L)
        assertThat(restoredMovie).isNotNull()
        assertThat(restoredMovie?.watchProgress).isEqualTo(5000L)
    }

    @Test
    fun syncAllWatchProgressFromHistory_clearsStaleMovieProgressWithoutHistory() = runTest {
        movieDao.insertAll(
            listOf(
                MovieEntity(
                    id = 7L,
                    name = "Stale Progress",
                    providerId = 3L,
                    watchProgress = 9_000L,
                    lastWatchedAt = 12_000L
                )
            )
        )

        movieDao.syncAllWatchProgressFromHistory()

        val movie = movieDao.getById(7L)
        assertThat(movie).isNotNull()
        assertThat(movie?.watchProgress).isEqualTo(0L)
        assertThat(movie?.lastWatchedAt).isEqualTo(0L)
    }

    @Test
    fun syncWatchProgressFromHistoryByProvider_updatesMatchingMoviesAndClearsStaleRows() = runTest {
        movieDao.insertAll(
            listOf(
                MovieEntity(
                    id = 7L,
                    name = "Watched Movie",
                    providerId = 3L,
                    watchProgress = 0L,
                    lastWatchedAt = 0L
                ),
                MovieEntity(
                    id = 8L,
                    name = "Stale Movie",
                    providerId = 3L,
                    watchProgress = 9_000L,
                    lastWatchedAt = 12_000L
                ),
                MovieEntity(
                    id = 9L,
                    name = "Other Provider",
                    providerId = 4L,
                    watchProgress = 4_000L,
                    lastWatchedAt = 5_000L
                )
            )
        )
        historyDao.insertOrUpdate(
            PlaybackHistoryEntity(
                contentId = 7L,
                contentType = ContentType.MOVIE,
                providerId = 3L,
                resumePositionMs = 18_000L,
                lastWatchedAt = 27_000L,
                watchCount = 2
            )
        )

        movieDao.syncWatchProgressFromHistoryByProvider(3L)

        val watchedMovie = movieDao.getById(7L)
        val staleMovie = movieDao.getById(8L)
        val otherProviderMovie = movieDao.getById(9L)

        assertThat(watchedMovie?.watchProgress).isEqualTo(18_000L)
        assertThat(watchedMovie?.lastWatchedAt).isEqualTo(27_000L)
        assertThat(staleMovie?.watchProgress).isEqualTo(0L)
        assertThat(staleMovie?.lastWatchedAt).isEqualTo(0L)
        assertThat(otherProviderMovie?.watchProgress).isEqualTo(4_000L)
        assertThat(otherProviderMovie?.lastWatchedAt).isEqualTo(5_000L)
    }
}
