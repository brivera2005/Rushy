package com.rushy.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class SyncProgress(
    val phase: String,
    val count: Int = 0,
)

@Composable
fun SyncErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sync failed",
            style = MaterialTheme.typography.headlineSmall,
            color = ThemeColors.CrimsonAccent,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ThemeColors.TextPrimary,
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun SyncProgressView(
    progress: SyncProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = progress.phase,
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        if (progress.count > 0) {
            Text(
                text = "%,d items".format(progress.count),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalRushyTheme.current.currentAccentColor,
            )
        }
        SkeletonBar(modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
fun SkeletonBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ThemeColors.SurfaceDark,
                        ThemeColors.CobaltAccent.copy(alpha = 0.35f),
                        ThemeColors.SurfaceDark,
                    ),
                ),
            ),
    )
}

@Composable
fun CategoryPillRow(
    categories: List<ChannelCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            val accent = LocalRushyTheme.current.currentAccentColor
            Button(
                onClick = { onSelect(category.id) },
                modifier = if (selected) {
                    Modifier.border(2.dp, accent, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = category.name,
                    color = if (selected) accent else ThemeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun HeroChannelRow(
    title: String,
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    nowPlaying: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = ThemeColors.TextPrimary,
        )
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.id }) { item ->
                HeroChannelCard(
                    item = item,
                    nowPlaying = nowPlaying[item.playbackId],
                    onClick = { onPlay(item) },
                )
            }
        }
    }
}

@Composable
private fun HeroChannelCard(
    item: MediaItem,
    nowPlaying: String?,
    onClick: () -> Unit,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    Button(onClick = onClick) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(ThemeColors.SurfaceDark, ThemeColors.DarkBackground),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaThumbnail(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                cardHeight = 100.dp,
            )
            nowPlaying?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = ThemeColors.EmeraldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SkeletonGrid(
    columns: Int = 5,
    rows: Int = 2,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThemeColors.SurfaceDark),
                    )
                }
            }
        }
    }
}

@Composable
fun FocusScaleBox(
    focused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    Box(modifier = modifier.scale(scale)) {
        content()
    }
}
