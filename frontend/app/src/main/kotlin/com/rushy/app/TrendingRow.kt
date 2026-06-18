package com.rushy.app

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@Composable
fun TrendingActionRow(
    title: String,
    items: List<TmdbMediaItem>,
    repository: LocalMediaRepository,
    credentials: CredentialStore,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolver = remember { TrendingContentResolver.getInstance(context) }

    LaunchedEffect(credentials.plexToken) {
        if (credentials.hasPlexCredentials()) {
            resolver.refreshWatchlistCache()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = ThemeColors.TextPrimary,
        )
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                var focused by remember(item.id) { mutableStateOf(false) }
                Button(
                    onClick = { /* selection handled by card buttons */ },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                ) {
                    TrendingActionCard(
                        item = item,
                        resolver = resolver,
                        repository = repository,
                        credentials = credentials,
                        onPlay = onPlay,
                        onRequest = { tmdbItem ->
                            scope.launch {
                                val result = resolver.requestOnPlex(tmdbItem)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        },
                        isFocused = focused,
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingActionCard(
    item: TmdbMediaItem,
    resolver: TrendingContentResolver,
    repository: LocalMediaRepository,
    credentials: CredentialStore,
    onPlay: (MediaItem) -> Unit,
    onRequest: (TmdbMediaItem) -> Unit,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var actionState by remember(item.id) { mutableStateOf(TrendingActionState(isResolving = true)) }
    val shape = RoundedCornerShape(ThemeColors.CornerRadius)

    LaunchedEffect(item.id, credentials.plexToken) {
        actionState = TrendingActionState(isResolving = true)
        actionState = resolver.resolveAction(item, repository)
    }

    Column(
        modifier = modifier
            .width(ThemeColors.PosterWidth)
            .clip(shape)
            .tvFocusHighlight(shape = shape, focused = isFocused)
            .background(ThemeColors.SurfaceDark)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ThemeColors.PosterHeight)
                .clip(shape)
                .background(ThemeColors.SurfaceElevated),
        ) {
            val poster = item.posterUrl
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(poster)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val playable = actionState.playableItem
            if (playable != null) {
                Button(
                    onClick = { onPlay(playable) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Watch",
                        color = ThemeColors.AccentTeal,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else if (!actionState.isResolving) {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "N/A",
                        color = ThemeColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            val requestLabel = when {
                actionState.isOnWatchlist -> "Requested ✓"
                !credentials.hasPlexCredentials() -> "Request"
                else -> "+ Request"
            }
            Button(
                onClick = {
                    if (!actionState.isOnWatchlist) {
                        onRequest(item)
                        actionState = actionState.copy(isOnWatchlist = true)
                    }
                },
                enabled = credentials.hasPlexCredentials() && !actionState.isOnWatchlist,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = requestLabel,
                    color = ThemeColors.AccentPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
