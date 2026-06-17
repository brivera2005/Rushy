package com.rushy.app

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class EpgLoadState(
    val phase: String = "Ready",
    val isLoading: Boolean = false,
    val programCount: Int = 0,
)

class EpgRepository private constructor(
    private val context: Context,
    private val credentials: CredentialStore,
) {
    private val gson = Gson()
    private val dao = MediaDatabase.getInstance(context).epgDao()
    private val mediaDao = MediaDatabase.getInstance(context).mediaDao()
    private val xmltvFile = File(context.filesDir, "xmltv_cache.xml")
    private val metaFile = File(context.filesDir, "xmltv_meta.json")
    private val syncMutex = Mutex()

    private val _loadState = MutableStateFlow(EpgLoadState())
    val loadState: StateFlow<EpgLoadState> = _loadState.asStateFlow()

    fun getProgramsForChannel(channelId: String): List<EpgProgram> =
        emptyList() // legacy sync API — use suspend methods

    suspend fun getProgramsForEpgChannel(epgChannelId: String, windowStart: Long, windowEnd: Long): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            if (epgChannelId.isBlank()) return@withContext emptyList()
            dao.getProgramsForChannel(epgChannelId, windowStart, windowEnd).map { it.toEpgProgram() }
        }

    suspend fun getProgramsForChannels(
        epgChannelIds: List<String>,
        windowStart: Long,
        windowEnd: Long,
    ): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        if (epgChannelIds.isEmpty()) return@withContext emptyMap()
        dao.getProgramsForChannels(epgChannelIds, windowStart, windowEnd)
            .map { it.toEpgProgram() }
            .groupBy { it.channelId }
    }

    suspend fun getOrFetchEpg(channel: MediaItem): List<EpgProgram> = withContext(Dispatchers.IO) {
        if (credentials.isDemoMode || !credentials.hasXtreamCredentials()) {
            return@withContext demoEpgForChannel(channel)
        }
        ensureXmltvParsed()
        val epgId = channel.epgChannelId.orEmpty()
        if (epgId.isBlank()) return@withContext emptyList()
        val now = System.currentTimeMillis() / 1000
        dao.getProgramsForChannel(epgId, now - 3600, now + 6 * 3600).map { it.toEpgProgram() }
    }

    suspend fun refreshEpg(channels: List<MediaItem>): Map<String, List<EpgProgram>> =
        withContext(Dispatchers.IO) {
            if (credentials.isDemoMode || !credentials.hasXtreamCredentials()) {
                return@withContext demoEpg(channels)
            }
            ensureXmltvParsed()
            val now = System.currentTimeMillis() / 1000
            val windowStart = now - 1800
            val windowEnd = now + 4 * 3600
            val epgIds = channels.mapNotNull { it.epgChannelId?.takeIf { id -> id.isNotBlank() } }.distinct()
            val byEpgId = getProgramsForChannels(epgIds, windowStart, windowEnd)
            channels.associate { channel ->
                val programs = channel.epgChannelId?.let { byEpgId[it] }.orEmpty()
                channel.playbackId to programs
            }
        }

    suspend fun ensureXmltvParsed(force: Boolean = false) {
        if (credentials.isDemoMode || !credentials.hasXtreamCredentials()) return
        syncMutex.withLock {
            val meta = loadMeta()
            val portalKey = portalFingerprint()
            val stale = meta == null ||
                meta.portalKey != portalKey ||
                System.currentTimeMillis() - meta.timestamp > CACHE_TTL_MS ||
                force
            if (!stale && dao.countPrograms() > 0) {
                _loadState.value = EpgLoadState("Guide ready", false, dao.countPrograms())
                return
            }

            _loadState.value = EpgLoadState("Loading guide...", true, 0)

            if (!xmltvFile.exists() || stale) {
                val ok = XmltvParser.downloadXmltv(
                    credentials.xtreamPortal,
                    credentials.xtreamUsername,
                    credentials.xtreamPassword,
                    xmltvFile,
                ) { phase -> _loadState.value = EpgLoadState(phase, true, 0) }
                if (!ok && !xmltvFile.exists()) {
                    _loadState.value = EpgLoadState("Guide download failed", false, 0)
                    return
                }
                saveMeta(XmltvMeta(portalKey, System.currentTimeMillis()))
            }

            _loadState.value = EpgLoadState("Parsing guide...", true, 0)
            val channelIds = mediaDao.getEpgChannelIds(MediaSource.XTREAM_LIVE.name).toSet()
            if (channelIds.isEmpty()) {
                _loadState.value = EpgLoadState("No EPG channel mapping", false, 0)
                return
            }

            val now = System.currentTimeMillis() / 1000
            val windowStart = now - 6 * 3600
            val windowEnd = now + 24 * 3600

            val programmes = XmltvParser.parseProgrammes(
                xmltvFile,
                channelIds,
                windowStart,
                windowEnd,
            ) { count ->
                _loadState.value = EpgLoadState("Parsing guide... ($count)", true, count)
            }

            dao.clearAll()
            programmes.chunked(500).forEach { batch -> dao.insertAll(batch) }
            val total = dao.countPrograms()
            _loadState.value = EpgLoadState("Guide ready", false, total)
            Log.i(TAG, "EPG loaded: $total programmes for ${channelIds.size} channels")
        }
    }

    private fun demoEpgForChannel(channel: MediaItem): List<EpgProgram> {
        val now = System.currentTimeMillis() / 1000
        return (0 until 3).map { index ->
            val start = now + index * 3600L
            EpgProgram(
                id = "${channel.id}_$index",
                channelId = channel.playbackId,
                title = "Program ${index + 1}",
                description = "Demo guide for ${channel.title}",
                startEpochSec = start,
                endEpochSec = start + 3600,
            )
        }
    }

    private fun demoEpg(channels: List<MediaItem>): Map<String, List<EpgProgram>> {
        val now = System.currentTimeMillis() / 1000
        return channels.associate { channel ->
            val programs = (0 until 4).map { index ->
                val start = now + index * 3600L
                EpgProgram(
                    id = "${channel.id}_$index",
                    channelId = channel.playbackId,
                    title = "Program ${index + 1}",
                    description = "Demo guide for ${channel.title}",
                    startEpochSec = start,
                    endEpochSec = start + 3600,
                )
            }
            channel.playbackId to programs
        }
    }

    private fun portalFingerprint(): String =
        "${credentials.xtreamPortal}|${credentials.xtreamUsername}"

    private fun loadMeta(): XmltvMeta? {
        if (!metaFile.exists()) return null
        return runCatching { gson.fromJson(metaFile.readText(), XmltvMeta::class.java) }.getOrNull()
    }

    private fun saveMeta(meta: XmltvMeta) {
        metaFile.writeText(gson.toJson(meta))
    }

    private data class XmltvMeta(val portalKey: String, val timestamp: Long)

    companion object {
        private const val TAG = "EpgRepository"
        private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L

        @Volatile
        private var instance: EpgRepository? = null

        fun getInstance(context: Context): EpgRepository {
            return instance ?: synchronized(this) {
                instance ?: EpgRepository(context.applicationContext, CredentialStore.getInstance(context))
                    .also { instance = it }
            }
        }
    }
}

private fun EpgProgramEntity.toEpgProgram() = EpgProgram(
    id = id,
    channelId = channelId,
    title = title,
    description = description,
    startEpochSec = startEpochSec,
    endEpochSec = endEpochSec,
)
