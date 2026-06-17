package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val EPG_BATCH_SIZE = 40
private const val TIMELINE_WIDTH_DP = 1920
private const val CHANNEL_COL_WIDTH_DP = 180

@Composable
fun TvGuideScreen(
    repository: LocalMediaRepository,
    epgRepository: EpgRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var epgData by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    var isLoadingChannels by remember { mutableStateOf(true) }
    var isLoadingEpg by remember { mutableStateOf(false) }
    val loadState by epgRepository.loadState.collectAsState()
    val timelineScroll = rememberScrollState()
    val listState = rememberTvLazyListState()
    val accent = LocalRushyTheme.current.currentAccentColor

    val nowSec = System.currentTimeMillis() / 1000
    val windowStart = nowSec - 1800
    val windowEnd = nowSec + 2 * 3600
    val slotMinutes = 30
    val slotCount = ((windowEnd - windowStart) / (slotMinutes * 60)).toInt()
    val timeSlots = (0 until slotCount).map { index ->
        windowStart + index * slotMinutes * 60
    }
    val totalWidth = (windowEnd - windowStart).toFloat()
    val nowFraction = ((nowSec - windowStart).toFloat() / totalWidth).coerceIn(0f, 1f)

    LaunchedEffect(Unit) {
        isLoadingChannels = true
        channels = repository.getLiveChannels(limit = 300)
        EpgSyncService.start(context, force = false)
        epgRepository.ensureXmltvParsed()
        isLoadingChannels = false
    }

    LaunchedEffect(channels, loadState.isLoading, loadState.programCount) {
        if (channels.isEmpty() || loadState.isLoading) return@LaunchedEffect
        isLoadingEpg = true
        val accumulated = mutableMapOf<String, List<EpgProgram>>()
        channels.chunked(EPG_BATCH_SIZE).forEach { batch ->
            val batchData = epgRepository.refreshEpg(batch)
            accumulated.putAll(batchData)
            epgData = accumulated.toMap()
        }
        isLoadingEpg = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "TV Guide",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ThemeColors.TextPrimary,
                )
                val statusText = when {
                    loadState.error != null -> "EPG error: ${loadState.error}"
                    isLoadingChannels || loadState.isLoading -> loadState.phase.ifBlank { "Loading guide..." }
                    loadState.programCount > 0 -> "EPG: ${loadState.programCount} programmes · ${loadState.channelCount} channels mapped"
                    else -> "${channels.size} channels · waiting for EPG data"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (loadState.error != null) ThemeColors.Error else ThemeColors.AccentPrimary,
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        EpgSyncService.start(context, force = true)
                        epgRepository.ensureXmltvParsed(force = true)
                    }
                },
            ) {
                Text("↻ Refresh Guide")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ThemeColors.SurfaceElevated)
                .padding(vertical = 4.dp),
        ) {
            Box(modifier = Modifier.width(CHANNEL_COL_WIDTH_DP.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(timelineScroll),
            ) {
                Box(modifier = Modifier.width(TIMELINE_WIDTH_DP.dp)) {
                    timeSlots.forEachIndexed { index, epoch ->
                        val fraction = index.toFloat() / slotCount.coerceAtLeast(1)
                        Text(
                            text = formatGuideTime(epoch),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (epoch <= nowSec && (timeSlots.getOrNull(index + 1) ?: Long.MAX_VALUE) > nowSec) {
                                ThemeColors.AccentPrimary
                            } else {
                                ThemeColors.TextSecondary
                            },
                            modifier = Modifier
                                .offset(x = (fraction * TIMELINE_WIDTH_DP).dp)
                                .padding(start = 4.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (nowFraction * TIMELINE_WIDTH_DP).dp)
                            .width(2.dp)
                            .height(28.dp)
                            .background(ThemeColors.LiveIndicator),
                    )
                }
            }
        }

        when {
            isLoadingChannels || (loadState.isLoading && epgData.isEmpty()) -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(12) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ThemeColors.SurfaceDark),
                        )
                    }
                }
            }
            channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No channels available. Sync your catalog first.",
                        color = ThemeColors.TextSecondary,
                    )
                }
            }
            else -> {
                if (isLoadingEpg && epgData.isEmpty()) {
                    Text("Loading programme data...", color = ThemeColors.TextMuted)
                }
                TvLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        val programs = epgData[channel.playbackId].orEmpty()
                            .filter { it.endEpochSec > windowStart && it.startEpochSec < windowEnd }

                        GuideChannelRow(
                            channel = channel,
                            programs = programs,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            nowSec = nowSec,
                            timelineScroll = timelineScroll,
                            onPlay = onPlay,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: MediaItem,
    programs: List<EpgProgram>,
    windowStart: Long,
    windowEnd: Long,
    nowSec: Long,
    timelineScroll: androidx.compose.foundation.ScrollState,
    onPlay: (MediaItem) -> Unit,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    val totalWidth = (windowEnd - windowStart).toFloat()
    val rowScroll = rememberScrollState()

    LaunchedEffect(timelineScroll.value) {
        if (rowScroll.value != timelineScroll.value) {
            rowScroll.scrollTo(timelineScroll.value)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ThemeColors.SurfaceDark),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onPlay(channel) },
            modifier = Modifier
                .width(CHANNEL_COL_WIDTH_DP.dp)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MediaThumbnailCompact(item = channel, size = 36.dp)
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = ThemeColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rowScroll),
        ) {
            Box(modifier = Modifier.width(TIMELINE_WIDTH_DP.dp).fillMaxHeight()) {
                if (programs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ThemeColors.SurfaceElevated)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (channel.epgChannelId.isNullOrBlank()) "No EPG ID" else "No programmes",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemeColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                } else {
                    programs.forEach { program ->
                        val startOffset = max(0f, (program.startEpochSec - windowStart).toFloat())
                        val endOffset = minOf(totalWidth, (program.endEpochSec - windowStart).toFloat())
                        val widthFraction = ((endOffset - startOffset) / totalWidth).coerceAtLeast(0.04f)
                        val leftFraction = startOffset / totalWidth
                        val isLive = program.startEpochSec <= nowSec && program.endEpochSec > nowSec

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(widthFraction)
                                .offset(x = (leftFraction * TIMELINE_WIDTH_DP).dp)
                                .padding(horizontal = 1.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isLive) accent.copy(alpha = 0.25f) else ThemeColors.SurfaceElevated,
                                )
                                .then(
                                    if (isLive) Modifier.border(1.dp, accent, RoundedCornerShape(4.dp))
                                    else Modifier,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column {
                                Text(
                                    text = program.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLive) ThemeColors.TextPrimary else ThemeColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isLive) {
                                    val remaining = ((program.endEpochSec - nowSec) / 60).coerceAtLeast(0)
                                    Text(
                                        text = "${remaining}m left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ThemeColors.AccentPrimary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatGuideTime(epochSec: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))
}
