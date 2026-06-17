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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TvGuideScreen(
    channels: List<MediaItem>,
    epgRepository: EpgRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var epgData by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(channels) {
        isLoading = true
        epgData = epgRepository.refreshEpg(channels)
        isLoading = false
    }

    val nowSec = System.currentTimeMillis() / 1000
    val windowStart = nowSec - 1800
    val windowEnd = nowSec + 4 * 3600
    val slotMinutes = 30
    val slotCount = ((windowEnd - windowStart) / (slotMinutes * 60)).toInt()
    val timeSlots = (0 until slotCount).map { index ->
        windowStart + index * slotMinutes * 60
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TV Guide",
                style = MaterialTheme.typography.titleLarge,
                color = ThemeColors.TextPrimary,
            )
            Text(
                text = if (isLoading) "Loading program data..." else "${channels.size} channels",
                color = ThemeColors.CobaltAccent,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(modifier = Modifier.width(200.dp))
            timeSlots.forEach { epoch ->
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .padding(4.dp),
                ) {
                    Text(
                        text = formatTime(epoch),
                        style = MaterialTheme.typography.labelSmall,
                        color = ThemeColors.CobaltAccent,
                    )
                }
            }
        }

        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(channels.take(60), key = { it.id }) { channel ->
                val programs = epgData[channel.playbackId].orEmpty()
                    .filter { it.endEpochSec > windowStart && it.startEpochSec < windowEnd }

                GuideChannelRow(
                    channel = channel,
                    programs = programs,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    onPlay = onPlay,
                )
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
    onPlay: (MediaItem) -> Unit,
) {
    val totalWidth = (windowEnd - windowStart).toFloat()
    val accent = LocalRushyTheme.current.currentAccentColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(ThemeColors.SurfaceDark)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaThumbnailCompact(item = channel)
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodySmall,
                color = ThemeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1200.dp),
            ) {
                programs.forEach { program ->
                    val startOffset = max(0f, (program.startEpochSec - windowStart).toFloat())
                    val endOffset = minOf(totalWidth, (program.endEpochSec - windowStart).toFloat())
                    val widthFraction = ((endOffset - startOffset) / totalWidth).coerceAtLeast(0.05f)
                    val leftFraction = startOffset / totalWidth

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(widthFraction)
                            .padding(horizontal = 1.dp)
                            .align(Alignment.CenterStart)
                            .padding(start = (leftFraction * 1200).dp)
                            .background(ThemeColors.DarkBackground)
                            .border(1.dp, accent.copy(alpha = 0.4f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Column {
                            Text(
                                text = program.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = ThemeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatTime(program.startEpochSec)} – ${formatTime(program.endEpochSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThemeColors.CobaltAccent,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(epochSec: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))
}
