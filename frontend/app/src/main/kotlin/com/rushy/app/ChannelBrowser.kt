package com.rushy.app



import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.lazy.grid.rememberLazyGridState

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.runtime.snapshotFlow

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.tv.foundation.lazy.list.TvLazyRow

import androidx.tv.foundation.lazy.list.items

import androidx.tv.material3.Button

import androidx.tv.material3.MaterialTheme

import androidx.tv.material3.Text

import kotlinx.coroutines.FlowPreview

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.debounce

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.launch



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



@OptIn(FlowPreview::class)

@Composable

fun ChannelBrowserScreen(

    summary: CatalogSummary,

    repository: LocalMediaRepository,

    epgRepository: EpgRepository,

    onPlay: (MediaItem) -> Unit,

    modifier: Modifier = Modifier,

) {

    val scope = rememberCoroutineScope()

    var searchInput by remember { mutableStateOf("") }

    var debouncedSearch by remember { mutableStateOf("") }

    var selectedCategoryId by remember { mutableStateOf("all") }

    var favoritesOnly by remember { mutableStateOf(false) }

    var channels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }

    var isLoadingMore by remember { mutableStateOf(false) }

    var hasMore by remember { mutableStateOf(true) }

    var totalInView by remember { mutableIntStateOf(0) }

    var nowPlaying by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var featured by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val gridState = rememberLazyGridState()



    LaunchedEffect(Unit) {

        featured = repository.getFeaturedLive(10)

    }



    LaunchedEffect(searchInput) {

        snapshotFlow { searchInput }

            .debounce(350)

            .distinctUntilChanged()

            .collect { debouncedSearch = it }

    }



    LaunchedEffect(selectedCategoryId, debouncedSearch, favoritesOnly) {

        isLoading = true

        hasMore = true

        try {

            val loaded = if (favoritesOnly) {

                repository.getFavorites(200).filter {

                    debouncedSearch.isBlank() || it.title.contains(debouncedSearch, ignoreCase = true)

                }

            } else {

                repository.getLiveChannels(selectedCategoryId, debouncedSearch, 0, LocalMediaRepository.PAGE_SIZE)

            }

            channels = loaded

            totalInView = if (favoritesOnly) loaded.size else {

                repository.countInCategory(MediaSource.XTREAM_LIVE, selectedCategoryId, debouncedSearch)

            }

            hasMore = !favoritesOnly && loaded.size >= LocalMediaRepository.PAGE_SIZE

        } catch (_: Exception) {

            channels = emptyList()

        } finally {

            isLoading = false

        }

    }



    LaunchedEffect(channels.take(8)) {

        if (channels.isEmpty()) return@LaunchedEffect

        val epg = mutableMapOf<String, String>()

        channels.take(8).forEach { channel ->

            val programs = epgRepository.getOrFetchEpg(channel)

            val now = System.currentTimeMillis() / 1000

            programs.firstOrNull { it.startEpochSec <= now && it.endEpochSec > now }?.let {

                epg[channel.playbackId] = it.title

            }

        }

        nowPlaying = epg

    }



    LaunchedEffect(gridState) {

        snapshotFlow {

            val info = gridState.layoutInfo

            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0

            last to info.totalItemsCount

        }.collect { (lastVisible, total) ->

            if (!hasMore || isLoadingMore || favoritesOnly || isLoading) return@collect

            if (lastVisible >= total - 12 && channels.size < totalInView) {

                isLoadingMore = true

                try {

                    val more = repository.getLiveChannels(

                        selectedCategoryId,

                        debouncedSearch,

                        channels.size,

                        LocalMediaRepository.PAGE_SIZE,

                    )

                    if (more.isNotEmpty()) {

                        channels = channels + more

                    }

                    hasMore = more.size >= LocalMediaRepository.PAGE_SIZE

                } finally {

                    isLoadingMore = false

                }

            }

        }

    }



    Column(

        modifier = modifier.fillMaxSize(),

        verticalArrangement = Arrangement.spacedBy(12.dp),

    ) {

        HeroChannelRow(

            title = "Featured",

            items = featured,

            onPlay = onPlay,

            nowPlaying = nowPlaying,

        )



        CategoryPillRow(

            categories = summary.liveCategories,

            selectedId = selectedCategoryId,

            onSelect = { selectedCategoryId = it },

        )



        Row(

            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.spacedBy(12.dp),

            verticalAlignment = Alignment.CenterVertically,

        ) {

            SearchField(

                query = searchInput,

                onQueryChange = { searchInput = it },

                placeholder = "Search ${summary.liveCount} channels...",

                modifier = Modifier.weight(1f),

            )

            Button(onClick = { favoritesOnly = !favoritesOnly }) {

                Text(if (favoritesOnly) "★ Favorites" else "☆ Favorites")

            }

            Text(

                text = "${channels.size} of $totalInView",

                color = ThemeColors.CobaltAccent,

            )

        }



        when {

            isLoading -> SkeletonGrid(columns = 7, rows = 3, modifier = Modifier.weight(1f))

            channels.isEmpty() -> Box(

                modifier = Modifier.weight(1f).fillMaxWidth(),

                contentAlignment = Alignment.Center,

            ) {

                Text("No channels in this category.", color = ThemeColors.CobaltAccent)

            }

            else -> VirtualizedMediaGrid(

                items = channels,

                columns = 7,

                gridState = gridState,

                onItemClick = onPlay,

                onToggleFavorite = { item ->

                    scope.launch {

                        repository.toggleFavorite(item.id, !item.isFavorite)

                        channels = channels.map {

                            if (it.id == item.id) it.copy(isFavorite = !item.isFavorite) else it

                        }

                    }

                },

                nowPlaying = nowPlaying,

                modifier = Modifier.weight(1f),

            )

        }



        if (isLoadingMore) {

            Text("Loading more...", color = ThemeColors.CobaltAccent)

        }

    }

}



@Composable

fun VirtualizedMediaGrid(

    items: List<MediaItem>,

    columns: Int,

    onItemClick: (MediaItem) -> Unit,

    onToggleFavorite: ((MediaItem) -> Unit)? = null,

    nowPlaying: Map<String, String> = emptyMap(),

    gridState: androidx.compose.foundation.lazy.grid.LazyGridState =

        rememberLazyGridState(),

    modifier: Modifier = Modifier,

) {

    LazyVerticalGrid(

        columns = GridCells.Fixed(columns),

        state = gridState,

        modifier = modifier.fillMaxSize(),

        horizontalArrangement = Arrangement.spacedBy(8.dp),

        verticalArrangement = Arrangement.spacedBy(8.dp),

    ) {

        items(items, key = { it.id }) { item ->

            var focused by remember(item.id) { mutableStateOf(false) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                Button(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                ) {
                    LiveChannelTile(
                        item = item,
                        subtitle = nowPlaying[item.playbackId],
                        isFocused = focused,
                    )
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
fun MoviesBrowserScreen(
    summary: CatalogSummary,
    repository: LocalMediaRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    SplitMediaBrowser(
        sidebarTitle = "Genres",
        categories = summary.vodCategories.ifEmpty {
            listOf(ChannelCategory("all", "All"))
        },
        source = MediaSource.XTREAM_VOD,
        repository = repository,
        onPlay = onPlay,
        modifier = modifier,
    )
}

@Composable
fun TvShowsBrowserScreen(
    summary: CatalogSummary,
    repository: LocalMediaRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    SplitMediaBrowser(
        sidebarTitle = "Genres",
        categories = summary.seriesCategories.ifEmpty {
            listOf(ChannelCategory("all", "All"))
        },
        source = MediaSource.XTREAM_SERIES,
        repository = repository,
        onPlay = onPlay,
        modifier = modifier,
    )
}

@Composable
private fun SplitMediaBrowser(
    sidebarTitle: String,
    categories: List<ChannelCategory>,
    source: MediaSource,
    repository: LocalMediaRepository,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryId by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(selectedCategoryId, source) {
        isLoading = true
        hasMore = true
        items = runCatching {
            repository.getItemsBySourceByRating(source, selectedCategoryId, "", 0, LocalMediaRepository.PAGE_SIZE)
        }.getOrElse { emptyList() }
        hasMore = items.size >= LocalMediaRepository.PAGE_SIZE
        isLoading = false
    }

    LaunchedEffect(gridState, selectedCategoryId, items.size, hasMore, isLoading) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (!hasMore || isLoadingMore || isLoading) return@collect
            if (lastVisible >= total - 8 && items.size % LocalMediaRepository.PAGE_SIZE == 0 && items.isNotEmpty()) {
                isLoadingMore = true
                try {
                    val more = repository.getItemsBySourceByRating(
                        source,
                        selectedCategoryId,
                        "",
                        items.size,
                        LocalMediaRepository.PAGE_SIZE,
                    )
                    if (more.isNotEmpty()) {
                        items = items + more
                    }
                    hasMore = more.size >= LocalMediaRepository.PAGE_SIZE
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground),
    ) {
        CategoryFilterSidebar(
            title = sidebarTitle,
            categories = categories,
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
                .padding(start = 12.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val categoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "All"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ThemeColors.TextPrimary,
                )
                Text(
                    text = if (isLoading) "Loading..." else "${items.size} titles",
                    style = MaterialTheme.typography.labelMedium,
                    color = ThemeColors.TextSecondary,
                )
            }

            when {
                isLoading -> PosterSkeletonGrid(modifier = Modifier.weight(1f))
                items.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No titles in this category. Tap Sync.", color = ThemeColors.TextSecondary)
                }
                else -> PosterResultsGrid(
                    items = items,
                    gridState = gridState,
                    onPlay = onPlay,
                    modifier = Modifier.weight(1f),
                )
            }

            if (isLoadingMore) {
                Text("Loading more...", color = ThemeColors.TextMuted)
            }
        }
    }
}

@Composable
private fun PosterSkeletonGrid(modifier: Modifier = Modifier) {
    SkeletonGrid(columns = 6, rows = 3, modifier = modifier)
}

@Composable
private fun PosterResultsGrid(
    items: List<MediaItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            var focused by remember(item.id) { mutableStateOf(false) }
            Button(
                onClick = { onPlay(item) },
                modifier = Modifier.onFocusChanged { focused = it.isFocused },
            ) {
                PosterCard(item = item, isFocused = focused)
            }
        }
    }
}



@Composable

private fun CategoryCarouselRow(

    title: String,

    repository: LocalMediaRepository,

    categoryId: String,

    source: MediaSource = MediaSource.XTREAM_VOD,

    onPlay: (MediaItem) -> Unit,

) {

    var items by remember(categoryId) { mutableStateOf<List<MediaItem>?>(null) }



    LaunchedEffect(categoryId, source) {

        items = repository.getItemsBySource(source, categoryId = categoryId, limit = 20)

        if (items.isNullOrEmpty() && source == MediaSource.XTREAM_VOD) {

            items = repository.getItemsBySource(MediaSource.XTREAM_SERIES, categoryId = categoryId, limit = 20)

        }

    }



    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Text(

            text = title,

            style = MaterialTheme.typography.titleMedium,

            color = ThemeColors.TextPrimary,

            maxLines = 1,

            overflow = TextOverflow.Ellipsis,

        )

        val loaded = items

        if (loaded == null) {

            SkeletonBar(modifier = Modifier.fillMaxWidth(0.4f))

        } else if (loaded.isNotEmpty()) {

            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                items(loaded, key = { it.id }) { item ->

                    Button(onClick = { onPlay(item) }) {

                        PosterCard(item = item)

                    }

                }

            }

        }

    }

}



@Composable

private fun CategoryResultsGrid(

    repository: LocalMediaRepository,

    categoryId: String,

    search: String,

    source: MediaSource,

    onPlay: (MediaItem) -> Unit,

) {

    var items by remember(categoryId, search) { mutableStateOf<List<MediaItem>?>(null) }



    LaunchedEffect(categoryId, search) {

        items = repository.getItemsBySource(source, categoryId = categoryId, search = search, limit = 80)

    }



    when (val loaded = items) {

        null -> SkeletonGrid(columns = 8, rows = 2)

        else -> VirtualizedMediaGrid(

            items = loaded,

            columns = 8,

            onItemClick = onPlay,

            modifier = Modifier.fillMaxSize(),

        )

    }

}



@Composable

fun PosterCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
) {
    Column(
        modifier = modifier.width(118.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            MediaThumbnailDense(item = item, modifier = Modifier.fillMaxWidth(), isFocused = isFocused)
            item.rating?.let { rating ->
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.labelSmall,
                    color = ThemeColors.EmeraldAccent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(ThemeColors.DarkBackground.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        item.plot?.take(80)?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.labelSmall,
                color = ThemeColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


