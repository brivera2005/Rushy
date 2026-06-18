package com.rushy.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sync failed",
            style = MaterialTheme.typography.headlineSmall,
            color = ThemeColors.Error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ThemeColors.TextSecondary,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                color = ThemeColors.AccentPrimary,
            )
        }
        SkeletonBar(modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
fun SkeletonBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ThemeColors.SurfaceDark,
                        ThemeColors.AccentPrimary.copy(alpha = 0.35f),
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
            var focused by remember(category.id) { mutableStateOf(false) }
            val shape = RoundedCornerShape(ThemeColors.CornerRadius)
            Button(
                onClick = { onSelect(category.id) },
                modifier = Modifier.onFocusChanged { focused = it.isFocused },
            ) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            when {
                                selected -> Modifier
                                    .background(ThemeColors.FocusBackground)
                                    .tvFocusHighlight(shape = shape, selected = true)
                                focused -> Modifier
                                    .background(ThemeColors.SurfaceElevated)
                                    .tvFocusHighlight(shape = shape, focused = true)
                                else -> Modifier.background(ThemeColors.SurfaceDark)
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = category.name,
                        color = if (selected) ThemeColors.FocusText else ThemeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun PlexHeroBanner(
    title: String,
    subtitle: String?,
    backdropUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ThemeColors.CardRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(ThemeColors.SurfaceDark),
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xEE1A1A2E),
                            Color(0x881A1A2E),
                            Color(0x331A1A2E),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = ThemeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ThemeColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(480.dp),
                )
            }
            Button(onClick = onPlay) {
                Text("▶  Play", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun TmdbDiscoveryRow(
    title: String,
    items: List<TmdbMediaItem>,
    onItemClick: (TmdbMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = ThemeColors.TextPrimary,
        )
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                Button(onClick = { onItemClick(item) }) {
                    TmdbPosterCard(item = item)
                }
            }
        }
    }
}

@Composable
fun TmdbPosterCard(
    item: TmdbMediaItem,
    modifier: Modifier = Modifier,
    width: Dp = ThemeColors.PosterWidth,
    isFocused: Boolean = false,
) {
    val shape = RoundedCornerShape(ThemeColors.CornerRadius)
    Column(
        modifier = modifier
            .width(width)
            .tvFocusHighlight(shape = shape, focused = isFocused),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(ThemeColors.PosterHeight)
                .clip(shape)
                .background(ThemeColors.SurfaceDark),
        ) {
            val url = item.posterUrl
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(120)
                        .size(500)
                        .build(),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.displayTitle.take(2).uppercase(),
                    modifier = Modifier.align(Alignment.Center),
                    color = ThemeColors.TextSecondary,
                )
            }
            if (item.voteAverage > 0) {
                Text(
                    text = "★ %.1f".format(item.voteAverage),
                    style = MaterialTheme.typography.labelSmall,
                    color = ThemeColors.AccentPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xCC1A1A2E))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.labelSmall,
            color = ThemeColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = ThemeColors.TextPrimary,
        )
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                Button(onClick = { onPlay(item) }) {
                    LiveTvCard(
                        item = item,
                        nowPlaying = nowPlaying[item.playbackId],
                    )
                }
            }
        }
    }
}

@Composable
fun LiveTvCard(
    item: MediaItem,
    nowPlaying: String? = null,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
) {
    LiveChannelTile(
        item = item,
        subtitle = nowPlaying,
        isFocused = isFocused,
        modifier = modifier,
    )
}

@Composable
fun DashboardStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ThemeColors.CornerRadius))
            .background(ThemeColors.SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = ThemeColors.AccentPrimary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ThemeColors.TextSecondary,
        )
    }
}

@Composable
fun SkeletonGrid(
    columns: Int = 7,
    rows: Int = 2,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .width(108.dp)
                            .height(140.dp)
                            .clip(RoundedCornerShape(ThemeColors.CornerRadius))
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "focusScale",
    )
    val accent = LocalRushyTheme.current.currentAccentColor
    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (focused) Modifier.border(ThemeColors.FocusRingWidth, accent, RoundedCornerShape(ThemeColors.CornerRadius))
                else Modifier,
            ),
    ) {
        content()
    }
}
