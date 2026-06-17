package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

private const val CROSSFADE_MS = 150

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    cardHeight: Dp = 72.dp,
    subtitle: String? = null,
    showTitle: Boolean = true,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    val shape = RoundedCornerShape(ThemeColors.CornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(ThemeColors.FocusRingWidth, accent, shape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .then(borderModifier)
            .background(ThemeColors.SurfaceDark)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(8.dp)),
            imageSize = 280,
        )
        if (showTitle) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                color = ThemeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = ThemeColors.AccentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Dense poster for 7-8 column grids */
@Composable
fun MediaThumbnailDense(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    cardHeight: Dp = 160.dp,
    subtitle: String? = null,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .width(108.dp)
            .clip(shape)
            .then(
                if (isFocused) Modifier.border(ThemeColors.FocusRingWidth, accent, shape)
                else Modifier,
            )
            .background(ThemeColors.SurfaceDark)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(6.dp)),
            imageSize = 200,
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
                color = ThemeColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MediaThumbnailCompact(
    item: MediaItem,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(ThemeColors.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        ThumbnailImage(
            item = item,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            imageSize = 96,
        )
    }
}

@Composable
private fun ThumbnailImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    imageSize: Int,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    Box(
        modifier = modifier.background(ThemeColors.DarkBackground),
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
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        } else {
            Text(
                text = item.title.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
    }
}
