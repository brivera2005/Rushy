package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

private const val CROSSFADE_MS = 120

/** 16:9 live channel logo — flat rectangle, no inner padding frame. */
@Composable
fun LiveChannelTile(
    item: MediaItem,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isFocused: Boolean = false,
) {
    val shape = RoundedCornerShape(ThemeColors.CornerRadius)
    Column(
        modifier = modifier
            .width(ThemeColors.LiveLogoWidth)
            .tvFocusHighlight(shape = shape, focused = isFocused),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier
                .width(ThemeColors.LiveLogoWidth)
                .height(ThemeColors.LiveLogoHeight)
                .clip(shape)
                .background(ThemeColors.SurfaceDark),
            imageSize = 320,
            contentScale = ContentScale.Crop,
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            color = ThemeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = ThemeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 2:3 poster rectangle for movies/series. */
@Composable
fun MediaThumbnailDense(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    cardHeight: Dp = ThemeColors.PosterHeight,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(ThemeColors.CornerRadius)
    Column(
        modifier = modifier
            .width(ThemeColors.PosterWidth)
            .tvFocusHighlight(shape = shape, focused = isFocused),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier
                .width(ThemeColors.PosterWidth)
                .height(ThemeColors.PosterHeight)
                .clip(shape)
                .background(ThemeColors.SurfaceDark),
            imageSize = 400,
            contentScale = ContentScale.Crop,
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            color = ThemeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = ThemeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    cardHeight: Dp = 72.dp,
    subtitle: String? = null,
    showTitle: Boolean = true,
) {
    LiveChannelTile(
        item = item,
        modifier = modifier,
        subtitle = subtitle,
        isFocused = isFocused,
    )
}

@Composable
fun MediaThumbnailCompact(
    item: MediaItem,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .width(size * 16f / 9f)
            .height(size)
            .clip(shape)
            .background(ThemeColors.SurfaceDark),
        contentAlignment = Alignment.Center,
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier.fillMaxSize(),
            imageSize = 128,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ThumbnailImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    imageSize: Int,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val logo = item.logoUrl?.takeIf { it.startsWith("http") }
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logo)
                    .crossfade(CROSSFADE_MS)
                    .size(imageSize)
                    .build(),
                contentDescription = item.title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = ThemeColors.TextSecondary,
            )
        }
    }
}
