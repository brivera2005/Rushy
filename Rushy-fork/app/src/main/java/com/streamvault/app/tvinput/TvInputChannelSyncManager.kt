package com.streamvault.app.tvinput

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.provider.BaseColumns
import android.util.Log
import com.streamvault.app.MainActivity
import com.streamvault.app.device.isTelevisionDevice
import com.streamvault.app.navigation.PlayerNavigationRequest
import com.streamvault.domain.model.Channel
import com.streamvault.domain.model.Program
import com.streamvault.domain.repository.ChannelRepository
import com.streamvault.domain.repository.EpgRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvInputChannelSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRepository: ProviderRepository,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository
) {

    suspend fun refreshTvInputCatalog() {
        refreshTvInputCatalogResult().onFailure { throwable ->
            Log.w(TAG, "TV input catalog sync failed", throwable)
        }
    }

    suspend fun refreshTvInputCatalogResult(): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            runCatching {
                if (!context.isTelevisionDevice()) {
                    return@runCatching
                }
                val provider = providerRepository.getActiveProvider().first()
                if (provider == null) {
                    deleteManagedChannels()
                    return@runCatching
                }

                val channels = channelRepository.getChannels(provider.id).first()
                val existingChannelIds = loadExistingChannels().toMutableMap()
                val targetKeys = channels.mapTo(mutableSetOf(), ::channelKey)

                existingChannelIds
                    .filterKeys { it !in targetKeys }
                    .values
                    .forEach(::deleteChannel)

                val programsByEpgId = loadPrograms(provider.id, channels)

                channels.forEach { channel ->
                    val key = channelKey(channel)
                    val channelId = ensureChannel(context.contentResolver, existingChannelIds[key], provider.id, channel)
                        ?: return@forEach
                    existingChannelIds[key] = channelId
                    replacePrograms(
                        channelId = channelId,
                        programs = programsByEpgId[channel.epgChannelId].orEmpty(),
                        providerId = provider.id,
                        channel = channel,
                        existingChannelIds = existingChannelIds
                    )
                }
            }
        }
    }

    private suspend fun loadPrograms(providerId: Long, channels: List<Channel>): Map<String, List<Program>> {
        val epgIds = channels.mapNotNull { it.epgChannelId?.takeIf(String::isNotBlank) }
        if (epgIds.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val start = now - PROGRAM_LOOKBACK_MS
        val end = now + PROGRAM_LOOKAHEAD_MS
        val merged = mutableMapOf<String, List<Program>>()

        epgIds.distinct().chunked(EPG_QUERY_CHUNK_SIZE).forEach { chunk ->
            merged += epgRepository.getProgramsForChannelsSnapshot(providerId, chunk, start, end)
        }
        return merged
    }

    private fun loadExistingChannels(): Map<String, Long> {
        val targetInputId = inputId()
        return context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(BaseColumns._ID, CHANNEL_COLUMN_INPUT_ID, CHANNEL_COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val inputIdIndex = cursor.getColumnIndexOrThrow(CHANNEL_COLUMN_INPUT_ID)
            val keyIndex = cursor.getColumnIndexOrThrow(CHANNEL_COLUMN_INTERNAL_PROVIDER_ID)
            buildMap {
                while (cursor.moveToNext()) {
                    if (cursor.getString(inputIdIndex) == targetInputId) {
                        put(cursor.getString(keyIndex), cursor.getLong(idIndex))
                    }
                }
            }
        }.orEmpty()
    }

    private fun insertChannel(providerId: Long, channel: Channel): Long? {
        val uri = context.contentResolver.insert(
            TvContract.Channels.CONTENT_URI,
            buildChannelValues(providerId, channel)
        ) ?: return null
        return ContentUris.parseId(uri)
    }

    private fun updateChannel(channelId: Long, providerId: Long, channel: Channel): Boolean {
        val rows = context.contentResolver.update(
            ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
            buildChannelValues(providerId, channel),
            null,
            null
        )
        return rows > 0 && channelExists(context.contentResolver, channelId)
    }

    private fun ensureChannel(
        resolver: ContentResolver,
        existingChannelId: Long?,
        providerId: Long,
        channel: Channel
    ): Long? {
        val refreshedId = existingChannelId
            ?.takeIf { channelExists(resolver, it) }
            ?.takeIf { updateChannel(it, providerId, channel) }
        return refreshedId ?: insertChannel(providerId, channel)
    }

    private fun replacePrograms(
        channelId: Long,
        programs: List<Program>,
        providerId: Long,
        channel: Channel,
        existingChannelIds: MutableMap<String, Long>
    ) {
        if (!shouldReplaceTvPrograms(channel, programs)) return

        val resolver = context.contentResolver
        var activeChannelId = channelId

        if (!channelExists(resolver, activeChannelId)) {
            activeChannelId = ensureChannel(resolver, null, providerId, channel) ?: return
            existingChannelIds[channelKey(channel)] = activeChannelId
        }

        resolver.query(
            TvContract.buildProgramsUriForChannel(activeChannelId),
            arrayOf(BaseColumns._ID),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            while (cursor.moveToNext()) {
                resolver.delete(
                    ContentUris.withAppendedId(TvContract.Programs.CONTENT_URI, cursor.getLong(idIndex)),
                    null,
                    null
                )
            }
        }

        programs
            .sortedBy { it.startTime }
            .take(MAX_PROGRAMS_PER_CHANNEL)
            .forEach { program ->
                try {
                    resolver.insert(
                        TvContract.Programs.CONTENT_URI,
                        buildProgramValues(activeChannelId, providerId, channel, program)
                    )
                } catch (throwable: Throwable) {
                    if (!channelExists(resolver, activeChannelId)) {
                        val recoveredChannelId = ensureChannel(resolver, null, providerId, channel) ?: return@forEach
                        existingChannelIds[channelKey(channel)] = recoveredChannelId
                        activeChannelId = recoveredChannelId
                        resolver.insert(
                            TvContract.Programs.CONTENT_URI,
                            buildProgramValues(activeChannelId, providerId, channel, program)
                        )
                    } else {
                        throw throwable
                    }
                }
            }
    }

    private fun channelExists(resolver: ContentResolver, channelId: Long): Boolean =
        resolver.query(
            ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
            arrayOf(BaseColumns._ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } == true

    private fun buildChannelValues(providerId: Long, channel: Channel): ContentValues = ContentValues().apply {
        put(CHANNEL_COLUMN_INPUT_ID, inputId())
        put(CHANNEL_COLUMN_TYPE, TvContract.Channels.TYPE_OTHER)
        put(CHANNEL_COLUMN_SERVICE_TYPE, TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
        put(CHANNEL_COLUMN_DISPLAY_NUMBER, channel.number.toString())
        put(CHANNEL_COLUMN_DISPLAY_NAME, channel.name)
        put(CHANNEL_COLUMN_DESCRIPTION, channel.categoryName ?: "IPTV")
        put(CHANNEL_COLUMN_INTERNAL_PROVIDER_ID, channelKey(channel))
        put(CHANNEL_COLUMN_INTERNAL_PROVIDER_DATA, encodeChannelData(providerId, channel))
        put(CHANNEL_COLUMN_APP_LINK_INTENT_URI, buildChannelIntent(channel).toUri(Intent.URI_INTENT_SCHEME))
    }

    private fun buildProgramValues(channelId: Long, providerId: Long, channel: Channel, program: Program): ContentValues = ContentValues().apply {
        put(PROGRAM_COLUMN_CHANNEL_ID, channelId)
        put(PROGRAM_COLUMN_TITLE, program.title)
        put(PROGRAM_COLUMN_DESCRIPTION, program.description)
        put(PROGRAM_COLUMN_START_TIME_UTC_MILLIS, program.startTime)
        put(PROGRAM_COLUMN_END_TIME_UTC_MILLIS, program.endTime)
        put(PROGRAM_COLUMN_INTERNAL_PROVIDER_DATA, "${providerId}:${channel.id}:${program.startTime}")
    }

    private fun buildChannelIntent(channel: Channel): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(
                MainActivity.EXTRA_PLAYER_REQUEST,
                PlayerNavigationRequest(
                    streamUrl = channel.streamUrl,
                    title = channel.name,
                    channelId = channel.epgChannelId,
                    internalId = channel.id,
                    categoryId = channel.categoryId,
                    providerId = channel.providerId,
                    contentType = "LIVE"
                )
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun deleteManagedChannels() {
        loadExistingChannels().values.forEach(::deleteChannel)
    }

    private fun deleteChannel(channelId: Long) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
            null,
            null
        )
    }

    private fun inputId(): String = ComponentName(context, StreamVaultTvInputService::class.java).flattenToShortString()

    private fun channelKey(channel: Channel): String = "${channel.providerId}:${channel.id}"

    private fun encodeChannelData(providerId: Long, channel: Channel): String =
        listOf(providerId, channel.id, channel.epgChannelId.orEmpty()).joinToString(ENTRY_SEPARATOR)

    private companion object {
        const val TAG = "TvInputChannelSync"
        const val CHANNEL_COLUMN_INPUT_ID = "input_id"
        const val CHANNEL_COLUMN_TYPE = "type"
        const val CHANNEL_COLUMN_SERVICE_TYPE = "service_type"
        const val CHANNEL_COLUMN_DISPLAY_NUMBER = "display_number"
        const val CHANNEL_COLUMN_DISPLAY_NAME = "display_name"
        const val CHANNEL_COLUMN_DESCRIPTION = "description"
        const val CHANNEL_COLUMN_BROWSABLE = "browsable"
        const val CHANNEL_COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id"
        const val CHANNEL_COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data"
        const val CHANNEL_COLUMN_APP_LINK_INTENT_URI = "app_link_intent_uri"
        const val PROGRAM_COLUMN_CHANNEL_ID = "channel_id"
        const val PROGRAM_COLUMN_TITLE = "title"
        const val PROGRAM_COLUMN_DESCRIPTION = "description"
        const val PROGRAM_COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis"
        const val PROGRAM_COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis"
        const val PROGRAM_COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data"
        const val EPG_QUERY_CHUNK_SIZE = 200
        const val MAX_PROGRAMS_PER_CHANNEL = 24
        const val PROGRAM_LOOKBACK_MS = 3 * 60 * 60 * 1000L
        const val PROGRAM_LOOKAHEAD_MS = 18 * 60 * 60 * 1000L
        const val ENTRY_SEPARATOR = ":"
        val syncMutex = Mutex()
    }
}

internal fun shouldReplaceTvPrograms(channel: Channel, programs: List<Program>): Boolean {
    if (programs.isNotEmpty()) return true
    return channel.epgChannelId.isNullOrBlank()
}
