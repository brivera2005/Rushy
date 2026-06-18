package com.streamvault.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streamvault.data.local.dao.ProgramDao
import com.streamvault.data.local.entity.ProgramEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ProgramDaoTest {
    private lateinit var db: StreamVaultDatabase
    private lateinit var programDao: ProgramDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, StreamVaultDatabase::class.java
        ).build()
        programDao = db.programDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeProgramsAndReadInList() = runTest {
        val program = ProgramEntity(
            providerId = 1L,
            channelId = "channel1",
            title = "Test Program",
            description = "Test Desc",
            startTime = 1000L,
            endTime = 2000L,
            lang = "en",
            hasArchive = false
        )
        programDao.insertAll(listOf(program))
        val byChannel = programDao.getForChannel(1L, "channel1", 500L, 2500L).first()
        assertThat(byChannel).hasSize(1)
        assertThat(byChannel[0].title).isEqualTo("Test Program")
    }

    @Test
    fun browseQueriesPreserveStoredProgramMetadata() = runTest {
        val program = ProgramEntity(
            providerId = 1L,
            channelId = "channel1",
            title = "Metadata Program",
            description = "Sports coverage",
            startTime = 1_000L,
            endTime = 2_000L,
            lang = "en",
            rating = "PG",
            imageUrl = "https://example.com/poster.png",
            genre = "Sports",
            category = "Highlights",
            hasArchive = true
        )
        programDao.insertAll(listOf(program))

        val byChannel = programDao.getForChannel(1L, "channel1", 0L, 3_000L).first().single()
        val search = programDao.searchPrograms(1L, "%Sports%", 0L, 3_000L, null, 10).first().single()
        val nowPlaying = programDao.getNowPlaying(1L, "channel1", 1_500L).first()

        assertThat(byChannel.rating).isEqualTo("PG")
        assertThat(byChannel.imageUrl).isEqualTo("https://example.com/poster.png")
        assertThat(byChannel.genre).isEqualTo("Sports")
        assertThat(byChannel.category).isEqualTo("Highlights")
        assertThat(search.rating).isEqualTo("PG")
        assertThat(search.imageUrl).isEqualTo("https://example.com/poster.png")
        assertThat(search.genre).isEqualTo("Sports")
        assertThat(search.category).isEqualTo("Highlights")
        assertThat(nowPlaying).isNotNull()
        assertThat(nowPlaying?.rating).isEqualTo("PG")
        assertThat(nowPlaying?.imageUrl).isEqualTo("https://example.com/poster.png")
        assertThat(nowPlaying?.genre).isEqualTo("Sports")
        assertThat(nowPlaying?.category).isEqualTo("Highlights")
    }

    @Test
    fun deleteOldPrograms() = runTest {
        val program1 = ProgramEntity(
            providerId = 1L,
            channelId = "channel1",
            title = "Old Program",
            startTime = 100L,
            endTime = 500L
        )
        val program2 = ProgramEntity(
            providerId = 1L,
            channelId = "channel1",
            title = "New Program",
            startTime = 1000L,
            endTime = 1500L
        )
        programDao.insertAll(listOf(program1, program2))
        
        // Delete older than 800L
        programDao.deleteOld(800L)
        
        val all = programDao.getForChannel(1L, "channel1", 0L, 2000L).first()
        assertThat(all).hasSize(1)
        assertThat(all[0].title).isEqualTo("New Program")
    }

    @Test
    fun getNowPlayingForChannels() = runTest {
        val program1 = ProgramEntity(
            providerId = 1L,
            channelId = "channel1",
            title = "Now Playing 1",
            startTime = 1000L,
            endTime = 2000L
        )
        val program2 = ProgramEntity(
            providerId = 1L,
            channelId = "channel2",
            title = "Now Playing 2",
            startTime = 1500L,
            endTime = 2500L
        )
        val program3 = ProgramEntity(
            providerId = 1L,
            channelId = "channel2",
            title = "Next Playing 2",
            startTime = 2500L,
            endTime = 3500L
        )
        programDao.insertAll(listOf(program1, program2, program3))

        // Check around time 1600L
        val nowPlaying = programDao.getNowPlayingForChannels(1L, listOf("channel1", "channel2"), 1600L).first()
        assertThat(nowPlaying).hasSize(2)
        assertThat(nowPlaying.map { it.title }).containsExactly("Now Playing 1", "Now Playing 2")

        // Check around time 2200L
        val laterPlaying = programDao.getNowPlayingForChannels(1L, listOf("channel1", "channel2"), 2200L).first()
        assertThat(laterPlaying).hasSize(1)
        assertThat(laterPlaying[0].title).isEqualTo("Now Playing 2")
    }
}
