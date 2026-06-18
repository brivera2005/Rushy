package com.rushy.app

import android.util.Log
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    onPlayCatchup: (MediaItem, EpgProgram) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    channelsOverride: List<MediaItem>? = null,
    showHeader: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var epgData by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    var isLoadingChannels by remember { mutableStateOf(true) }
    var isLoadingEpg by remember { mutableStateOf(false) }
    var epgLoadError by remember { mutableStateOf<String?>(null) }
    var focusedChannelId by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(channelsOverride) {
        if (channelsOverride != null) {
            channels = channelsOverride
            isLoadingChannels = false
            return@LaunchedEffect
        }
        isLoadingChannels = true
        channels = runCatching { repository.getLiveChannels(limit = 300) }.getOrElse { emptyList() }
        isLoadingChannels = false
    }

    LaunchedEffect(channelsOverride, channels) {
        if (channels.isEmpty()) return@LaunchedEffect
        epgLoadError = null
        runCatching {
            EpgSyncService.start(context, force = false)
            epgRepository.ensureXmltvParsed()
        }.onFailure { e ->
            Log.w("TvGuideScreen", "EPG load failed", e)
            epgLoadError = e.message ?: "Failed to load TV guide"
        }
    }

    LaunchedEffect(channels, loadState.isLoading, loadState.programCount, loadState.error) {
        if (channels.isEmpty() || loadState.isLoading) return@LaunchedEffect
        isLoadingEpg = true
        epgLoadError = loadState.error
        runCatching {
            val accumulated = mutableMapOf<String, List<EpgProgram>>()
            channels.chunked(EPG_BATCH_SIZE).forEach { batch ->
                val batchData = epgRepository.refreshEpg(batch)
                accumulated.putAll(batchData)
                epgData = accumulated.toMap()
            }
        }.onFailure { e ->
            Log.w("TvGuideScreen", "EPG refresh failed", e)
            epgLoadError = e.message ?: "Failed to load programme data"
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
        val bannerError = epgLoadError ?: loadState.error
        if (!showHeader && bannerError != null) {
            Text(
                text = "EPG: $bannerError",
                style = MaterialTheme.typography.labelMedium,
                color = ThemeColors.Error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (showHeader) {
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
                        color = if (loadState.error != null) ThemeColors.Error else ThemeColors.TextSecondary,
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
        }

        val focusedChannel = channels.firstOrNull { it.id == focusedChannelId }
        if (focusedChannel != null) {
            val focusedPrograms = epgData[focusedChannel.playbackId].orEmpty().sortedBy { it.startEpochSec }
            NowNextPanel(
                channel = focusedChannel,
                programs = focusedPrograms,
                nowSec = nowSec,
            )
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
                            onPlayCatchup = onPlayCatchup,
                            onFocused = { focusedChannelId = channel.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowNextPanel(
    channel: MediaItem,
    programs: List<EpgProgram>,
    nowSec: Long,
) {
    val current = programs.firstOrNull { it.startEpochSec <= nowSec && it.endEpochSec > nowSec }
    val next = programs.firstOrNull { it.startEpochSec > nowSec }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(ThemeColors.SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnailCompact(item = channel, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ThemeColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.tvArchive) {
                    Text(
                        text = "⏪ Catch-up",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThemeColors.AccentPrimary,
                    )
                }
            }
            Text(
                text = buildString {
                    append("Now: ")
                    append(current?.title ?: "—")
                    next?.let { append("  ·  Next: ${it.title}") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = ThemeColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
    onPlayCatchup: (MediaItem, EpgProgram) -> Unit,
    onFocused: () -> Unit,
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
            .background(ThemeColors.SurfaceDark)
            .onFocusChanged { if (it.isFocused) onFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onPlay(channel) },
            modifier = Modifier
                .width(CHANNEL_COL_WIDTH_DP.dp)
                .fillMaxHeight()
                .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MediaThumbnailCompact(item = channel, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = ThemeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (channel.tvArchive) {
                        Text(
                            text = "⏪",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemeColors.AccentPrimary,
                        )
                    }
                }
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
                        val widthFraction = ((endOffset - startOffset) / totalWidth).coerceIn(0.04f, 1f)
                        val leftFraction = startOffset / totalWidth
                        val isLive = program.startEpochSec <= nowSec && program.endEpochSec > nowSec
                        val isPast = program.endEpochSec <= nowSec
                        val canCatchup = isPast && channel.tvArchive

                        GuideProgramCell(
                            program = program,
                            isLive = isLive,
                            canCatchup = canCatchup,
                            nowSec = nowSec,
                            widthFraction = widthFraction,
                            leftFraction = leftFraction,
                            onPlayLive = { onPlay(channel) },
                            onPlayCatchup = { onPlayCatchup(channel, program) },
                            onFocused = onFocused,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideProgramCell(
    program: EpgProgram,
    isLive: Boolean,
    canCatchup: Boolean,
    nowSec: Long,
    widthFraction: Float,
    leftFraction: Float,
    onPlayLive: () -> Unit,
    onPlayCatchup: () -> Unit,
    onFocused: () -> Unit,
) {
    val bgColor = when {
        isLive -> ThemeColors.SurfaceElevated
        canCatchup -> ThemeColors.SurfaceElevated.copy(alpha = 0.7f)
        else -> ThemeColors.SurfaceDark
    }
    val content = @Composable {
        Column {
            Text(
                text = program.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive || canCatchup) ThemeColors.TextPrimary else ThemeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                isLive -> {
                    val remaining = ((program.endEpochSec - nowSec) / 60).coerceAtLeast(0)
                    Text(
                        text = "${remaining}m left",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThemeColors.AccentPrimary,
                        maxLines = 1,
                    )
                }
                canCatchup -> {
                    Text(
                        text = "Watch",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThemeColors.AccentPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    val cellModifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(widthFraction)
        .offset(x = (leftFraction * TIMELINE_WIDTH_DP).dp)
        .padding(horizontal = 1.dp, vertical = 4.dp)

    if (isLive || canCatchup) {
        Button(
            onClick = { if (canCatchup) onPlayCatchup() else onPlayLive() },
            modifier = cellModifier
                .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor)
                    .then(
                        if (isLive) Modifier.background(ThemeColors.FocusBackground.copy(alpha = 0.35f))
                        else Modifier,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                content()
            }
        }
    } else {
        Box(
            modifier = cellModifier
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

private fun formatGuideTime(epochSec: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))
}
