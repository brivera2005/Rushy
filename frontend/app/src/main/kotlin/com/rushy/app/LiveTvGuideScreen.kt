package com.rushy.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Live TV: vertical category list (left) + XMLTV programme guide (right).
 */
@Composable
fun LiveTvGuideScreen(
    summary: CatalogSummary,
    repository: LocalMediaRepository,
    epgRepository: EpgRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember { mutableStateOf("all") }
    var channels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedCategoryId) {
        isLoading = true
        channels = runCatching {
            repository.getLiveChannels(selectedCategoryId, "", 0, 250)
        }.getOrElse { emptyList() }
        isLoading = false
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground),
    ) {
        LiveCategorySidebar(
            categories = summary.liveCategories,
            selectedId = selectedCategoryId,
            onSelect = { selectedCategoryId = it },
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(ThemeColors.SidebarBackground)
                .padding(vertical = 8.dp, horizontal = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val categoryName = summary.liveCategories
                    .firstOrNull { it.id == selectedCategoryId }?.name ?: "All Channels"
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ThemeColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isLoading) "Loading..." else "${channels.size} channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = ThemeColors.TextSecondary,
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading channels...", color = ThemeColors.TextSecondary)
                }
            } else if (channels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels in this category. Tap Sync.", color = ThemeColors.TextSecondary)
                }
            } else {
                TvGuideScreen(
                    repository = repository,
                    epgRepository = epgRepository,
                    onPlay = onPlay,
                    channelsOverride = channels,
                    showHeader = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveCategorySidebar(
    categories: List<ChannelCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedId
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(ThemeColors.CornerRadius)
                Button(
                    onClick = { onSelect(category.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .onFocusChanged { focused = it.isFocused },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .then(
                                when {
                                    selected -> Modifier.background(ThemeColors.FocusBackground)
                                    focused -> Modifier
                                        .background(ThemeColors.SurfaceElevated)
                                        .tvFocusHighlight(shape = shape, focused = true)
                                    else -> Modifier.background(ThemeColors.SurfaceDark)
                                },
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (selected) ThemeColors.FocusText else ThemeColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
