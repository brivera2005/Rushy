package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

enum class AppScreen(val label: String) {
    HOME("Home"),
    LIVE_TV("Live TV"),
    GUIDE("TV Guide"),
    MOVIES("Movies"),
    SETTINGS("Settings"),
}

@Composable
fun AppNavBar(
    current: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    settingsHasUpdate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = LocalRushyTheme.current.currentAccentColor
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppScreen.entries.forEach { screen ->
            val selected = screen == current
            val hasUpdateBadge = screen == AppScreen.SETTINGS && settingsHasUpdate
            val label = if (hasUpdateBadge) "${screen.label} *" else screen.label
            Button(
                onClick = { onNavigate(screen) },
                modifier = when {
                    selected -> Modifier.border(2.dp, accent, MaterialTheme.shapes.small)
                    hasUpdateBadge -> Modifier.border(2.dp, ThemeColors.EmeraldAccent, MaterialTheme.shapes.small)
                    else -> Modifier
                },
            ) {
                Text(
                    text = label,
                    color = when {
                        selected -> accent
                        hasUpdateBadge -> ThemeColors.EmeraldAccent
                        else -> ThemeColors.TextPrimary
                    },
                )
            }
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ThemeColors.TextPrimary),
        cursorBrush = SolidColor(LocalRushyTheme.current.currentAccentColor),
        modifier = modifier
            .fillMaxWidth()
            .background(ThemeColors.SurfaceDark)
            .padding(12.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) {
                Text(text = placeholder, color = ThemeColors.CobaltAccent)
            }
            inner()
        },
    )
}

@Composable
fun ChannelBrowserScreen(
    dashboard: DashboardData,
    repository: LocalMediaRepository,
    onPlay: (MediaItem) -> Unit,
    onDataChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("all") }
    var favoritesOnly by remember { mutableStateOf(false) }

    val categories = dashboard.categoryGroups.map { it.category }
    val baseChannels = dashboard.categoryGroups
        .firstOrNull { it.category.id == selectedCategoryId }
        ?.channels
        ?: dashboard.liveTv

    val filtered = remember(baseChannels, searchQuery, favoritesOnly) {
        var list = baseChannels
        if (favoritesOnly) list = list.filter { it.isFavorite }
        if (searchQuery.isNotBlank()) {
            val results = LocalSearchEngine.search(list, searchQuery)
            results.exactMatches + results.nearMatches
        } else {
            list
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search ${dashboard.liveTv.size} channels...",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { favoritesOnly = !favoritesOnly }) {
                Text(if (favoritesOnly) "★ Favorites" else "☆ Favorites")
            }
            Text(
                text = "${filtered.size} channels",
                color = ThemeColors.CobaltAccent,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvLazyColumn(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(ThemeColors.SurfaceDark)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    val selected = category.id == selectedCategoryId
                    Button(
                        onClick = { selectedCategoryId = category.id },
                        modifier = if (selected) {
                            Modifier.border(
                                2.dp,
                                LocalRushyTheme.current.currentAccentColor,
                                MaterialTheme.shapes.small,
                            )
                        } else {
                            Modifier
                        },
                    ) {
                        Text(
                            text = category.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = ThemeColors.TextPrimary,
                        )
                    }
                }
            }

            VirtualizedMediaGrid(
                items = filtered,
                columns = 5,
                onItemClick = onPlay,
                onToggleFavorite = { item ->
                    repository.toggleFavorite(item.id, !item.isFavorite)
                    onDataChanged()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun VirtualizedMediaGrid(
    items: List<MediaItem>,
    columns: Int,
    onItemClick: (MediaItem) -> Unit,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No items found.", color = ThemeColors.CobaltAccent)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onItemClick(item) }) {
                    MediaThumbnail(item = item, modifier = Modifier.width(140.dp))
                }
                if (onToggleFavorite != null) {
                    Button(onClick = { onToggleFavorite(item) }) {
                        Text(
                            text = if (item.isFavorite) "★ Unfavorite" else "☆ Favorite",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VodBrowserScreen(
    items: List<MediaItem>,
    title: String,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else {
            val results = LocalSearchEngine.search(items, searchQuery)
            results.exactMatches + results.nearMatches
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = ThemeColors.TextPrimary)
        SearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search ${items.size} titles...",
        )
        VirtualizedMediaGrid(
            items = filtered,
            columns = 6,
            onItemClick = onPlay,
            modifier = Modifier.weight(1f),
        )
    }
}
