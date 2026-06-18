package com.streamvault.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ProviderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ProviderDaoTest {
    private lateinit var db: StreamVaultDatabase
    private lateinit var providerDao: ProviderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StreamVaultDatabase::class.java).build()
        providerDao = db.providerDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun setActive_keepsExactlyOneLiveProviderAcrossRepeatedSwitches() = runTest {
        providerDao.insert(provider(id = 1L, name = "One", isActive = true))
        providerDao.insert(provider(id = 2L, name = "Two", isActive = false))
        providerDao.insert(provider(id = 3L, name = "Three", isActive = true))

        providerDao.setActive(2L)

        var providers = providerDao.getAllSync()
        assertThat(providers.filter { it.isActive && it.type != ProviderType.PLEX }.map(ProviderEntity::id))
            .containsExactly(2L)
        assertThat(providerDao.getActive().first()?.id).isEqualTo(2L)

        providerDao.setActive(3L)

        providers = providerDao.getAllSync()
        assertThat(providers.filter { it.isActive && it.type != ProviderType.PLEX }.map(ProviderEntity::id))
            .containsExactly(3L)
        assertThat(providerDao.getActive().first()?.id).isEqualTo(3L)
    }

    @Test
    fun insertAndUpdate_normalizeLiveProviderUniqueness() = runTest {
        providerDao.insert(provider(id = 1L, name = "One", isActive = true))
        providerDao.insert(provider(id = 2L, name = "Two", isActive = true))

        var providers = providerDao.getAllSync()
        assertThat(providers.filter { it.isActive && it.type != ProviderType.PLEX }.map(ProviderEntity::id))
            .containsExactly(2L)

        providerDao.update(provider(id = 1L, name = "One", isActive = true))

        providers = providerDao.getAllSync()
        assertThat(providers.filter { it.isActive && it.type != ProviderType.PLEX }.map(ProviderEntity::id))
            .containsExactly(1L)
        assertThat(providerDao.getActive().first()?.id).isEqualTo(1L)
    }

    @Test
    fun setActive_allowsPlexBackupAlongsideLiveProvider() = runTest {
        providerDao.insert(provider(id = 1L, name = "Rushy IPTV", isActive = true))
        providerDao.insert(
            provider(
                id = 2L,
                name = "Plex Backup",
                type = ProviderType.PLEX,
                isActive = false,
            )
        )

        providerDao.setBackupActive(2L)

        val providers = providerDao.getAllSync()
        assertThat(providers.filter(ProviderEntity::isActive).map(ProviderEntity::id)).containsExactly(1L, 2L)
        assertThat(providerDao.getActive().first()?.id).isEqualTo(1L)
        assertThat(providerDao.getActiveBackup().first()?.id).isEqualTo(2L)

        providerDao.setActive(1L)

        assertThat(providerDao.getAllSync().filter(ProviderEntity::isActive).map(ProviderEntity::id))
            .containsExactly(1L, 2L)
    }

    private fun provider(
        id: Long,
        name: String,
        type: ProviderType = ProviderType.M3U,
        isActive: Boolean,
    ) = ProviderEntity(
        id = id,
        name = name,
        type = type,
        serverUrl = "https://example.com/$id.m3u",
        m3uUrl = "https://example.com/$id.m3u",
        isActive = isActive
    )
}
