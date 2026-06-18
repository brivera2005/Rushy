package com.rushy.app



import android.util.Log

import android.app.Activity

import android.content.Intent

import android.os.Bundle
import android.view.KeyEvent

import android.speech.RecognizerIntent

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.runtime.Composable

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.SideEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import androidx.tv.foundation.lazy.list.TvLazyRow

import androidx.tv.foundation.lazy.list.items

import androidx.tv.material3.Button

import androidx.tv.material3.MaterialTheme

import androidx.tv.material3.Surface

import androidx.tv.material3.Text

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

import kotlinx.coroutines.CoroutineExceptionHandler

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import kotlinx.coroutines.plus

import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {

    private var onSpeechResultReceived: ((String) -> Unit)? = null

    @Volatile
    private var blockKeysDuringForceUpdate: Boolean = false

    fun setForceUpdateKeyBlock(block: Boolean) {
        blockKeysDuringForceUpdate = block
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (blockKeysDuringForceUpdate) return true
        return super.dispatchKeyEvent(event)
    }



    private val speechRecognizerLauncher = registerForActivityResult(

        ActivityResultContracts.StartActivityForResult(),

    ) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            val spokenText = result.data

                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {

                onSpeechResultReceived?.invoke(spokenText)

            }

        }

    }



    fun triggerVoiceSearch(onResult: (String) -> Unit) {

        onSpeechResultReceived = onResult

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Rushy...")

        }

        try {

            speechRecognizerLauncher.launch(intent)

        } catch (_: Exception) {

            Toast.makeText(this, "Voice recognition hardware is unavailable.", Toast.LENGTH_SHORT).show()

        }

    }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)



        setContent {

            val themeState = remember { RushyThemeState() }

            val context = LocalContext.current

            val credentials = remember { CredentialStore.getInstance(context).also { it.ensureDevCredentialsIfNeeded() } }

            var isConfigured by remember { mutableStateOf(credentials.isConfigured()) }

            var showSplash by remember { mutableStateOf(true) }



            LaunchedEffect(Unit) {

                delay(900)

                showSplash = false

            }



            CompositionLocalProvider(LocalRushyTheme provides themeState) {

                Surface(modifier = Modifier.fillMaxSize()) {

                    when {

                        showSplash -> {

                            Box(

                                modifier = Modifier

                                    .fillMaxSize()

                                    .background(ThemeColors.DarkBackground),

                                contentAlignment = Alignment.Center,

                            ) {

                                RushyLogo(size = RushyLogoSize.Splash, showTagline = true)

                            }

                        }

                        !isConfigured -> {

                            SetupWizardView(onSetupComplete = { isConfigured = true })

                        }

                        else -> {

                            RushyApp(

                                onTriggerVoiceSearch = { query ->

                                    this@MainActivity.triggerVoiceSearch { spokenText ->

                                        query(spokenText)

                                    }

                                },

                            )

                        }

                    }

                }

            }

        }

    }

}



private enum class AppInitPhase { Preparing, Ready, Failed }



@Composable

fun RushyApp(onTriggerVoiceSearch: ((String) -> Unit) -> Unit) {

    val context = LocalContext.current

    val activity = context as? Activity

    val scope = rememberCoroutineScope()

    var repository by remember { mutableStateOf<LocalMediaRepository?>(null) }

    var initPhase by remember { mutableStateOf(AppInitPhase.Preparing) }

    var initError by remember { mutableStateOf<String?>(null) }

    var startupAttempt by remember { mutableIntStateOf(0) }

    val credentials = remember { CredentialStore.getInstance(context) }

    val playerSettings = remember { PlayerSettings.getInstance(context) }

    val epgRepository = remember { EpgRepository.getInstance(context) }

    val playback = remember { PlaybackRouter.getInstance(context) }

    val updatePrefs = remember { UpdatePreferences.getInstance(context) }

    val updateManager = remember { ApkUpdateManager(context) }



    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    var summary by remember { mutableStateOf<CatalogSummary?>(null) }

    var isSyncing by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }

    var syncProgress by remember { mutableStateOf(SyncProgress("Starting sync...")) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<SearchResult?>(null) }

    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    var isForceUpdating by remember { mutableStateOf(false) }

    var needsInstallPermission by remember { mutableStateOf(false) }

    var forceUpdateStatus by remember { mutableStateOf<String?>(null) }

    var isCheckingUpdate by remember { mutableStateOf(false) }

    var isDownloadingUpdate by remember { mutableStateOf(false) }

    var downloadProgress by remember { mutableIntStateOf(-1) }

    val activeAccentColor = LocalRushyTheme.current.currentAccentColor



    val syncExceptionHandler = remember {

        CoroutineExceptionHandler { _, throwable ->

            scope.launch(Dispatchers.Main.immediate) {

                val msg = throwable.message ?: "Sync failed."

                errorMessage = msg

                isSyncing = false

                isRefreshing = false

                AppDiagnostics.recordError(context, msg)

            }

        }

    }

    val syncScope = remember(scope) { scope + SupervisorJob() + syncExceptionHandler }



    suspend fun performUpdate(updateInfo: UpdateInfo) {

        if (activity != null && !updateManager.canInstallPackages()) {

            needsInstallPermission = true

            forceUpdateStatus = null

            return

        }

        needsInstallPermission = false

        isDownloadingUpdate = true

        downloadProgress = 0

        forceUpdateStatus = null

        when (val download = updateManager.downloadApk(updateInfo) { progress ->

            scope.launch(Dispatchers.Main.immediate) {

                downloadProgress = progress

            }

        }) {

            is DownloadResult.Success -> {

                updateManager.installApk(download.apkFile)

                isDownloadingUpdate = false

                downloadProgress = -1

            }

            is DownloadResult.Error -> {

                isDownloadingUpdate = false

                downloadProgress = -1

                forceUpdateStatus = "${download.message} — retrying..."

                delay(3000)

                performUpdate(updateInfo)

            }

        }

    }



    fun beginForceUpdate(updateInfo: UpdateInfo) {

        pendingUpdate = updateInfo

        isForceUpdating = true

        needsInstallPermission = false

        forceUpdateStatus = null

        scope.launch { performUpdate(updateInfo) }

    }



    fun handleUpdateCheckResult(result: UpdateCheckResult) {

        when (result) {

            is UpdateCheckResult.UpdateAvailable -> {

                if (pendingUpdate?.versionCode != result.info.versionCode) {

                    beginForceUpdate(result.info)

                } else if (!isForceUpdating) {

                    beginForceUpdate(result.info)

                }

            }

            is UpdateCheckResult.UpToDate -> {

                if (!isForceUpdating) {

                    pendingUpdate = null

                    needsInstallPermission = false

                }

            }

            is UpdateCheckResult.Error -> Unit

        }

    }



    fun checkForUpdates() {

        if (isCheckingUpdate || isForceUpdating) return

        scope.launch {

            isCheckingUpdate = true

            val result = updateManager.checkForUpdate()

            isCheckingUpdate = false

            handleUpdateCheckResult(result)

        }

    }



    fun loadCatalog(showRefreshIndicator: Boolean = false, liveOnly: Boolean = false) {

        val repo = repository ?: return

        syncScope.launch {

            withContext(Dispatchers.Main.immediate) {

                if (showRefreshIndicator) isRefreshing = true

                isSyncing = true

                errorMessage = null

            }

            try {

                val result = repo.syncCatalog(
                    onProgress = { progress ->
                        scope.launch(Dispatchers.Main.immediate) {
                            syncProgress = progress
                        }
                    },
                    liveOnly = liveOnly,
                )

                withContext(Dispatchers.Main.immediate) {

                    summary = result

                    AppDiagnostics.clearError(context)

                }

                // Background EPG load after catalog sync
                if (result.liveCount > 0) {
                    syncScope.launch {
                        try {
                            EpgSyncService.start(context, force = false)
                            epgRepository.ensureXmltvParsed()
                        } catch (e: Exception) {
                            Log.w("RushyApp", "Background EPG sync failed", e)
                        }
                    }
                }

                if (liveOnly && result.liveCount > 0) {

                    syncScope.launch {

                        try {

                            val full = repo.syncVodAndSeries { progress ->

                                scope.launch(Dispatchers.Main.immediate) {

                                    syncProgress = progress

                                }

                            }

                            withContext(Dispatchers.Main.immediate) {

                                summary = full

                            }

                        } catch (e: Exception) {

                            Log.w("RushyApp", "Background VOD/series sync failed", e)

                        }

                    }

                }

            } catch (e: Exception) {

                val msg = e.message ?: "Sync failed."

                withContext(Dispatchers.Main.immediate) {

                    errorMessage = msg

                    AppDiagnostics.recordError(context, msg)

                    try {

                        summary = repo.getSummary()

                    } catch (_: Exception) {

                        summary = summary ?: CatalogSummary()

                    }

                }

            } finally {

                withContext(Dispatchers.Main.immediate) {

                    isSyncing = false

                    isRefreshing = false

                }

                if (showRefreshIndicator) {
                    checkForUpdates()
                }

            }

        }

    }



    fun playItem(item: MediaItem) {

        playback.play(item)

    }



    LaunchedEffect(Unit) {

        if (updatePrefs.checkOnStartup) {

            checkForUpdates()

        }

    }



    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME && !isForceUpdating) {

                checkForUpdates()

            }

        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }

    }



    SideEffect {

        (activity as? MainActivity)?.setForceUpdateKeyBlock(

            isForceUpdating && pendingUpdate != null && !needsInstallPermission,

        )

    }



    if (isForceUpdating && pendingUpdate != null) {

        ForceUpdateScreen(

            updateInfo = pendingUpdate!!,

            downloadProgress = downloadProgress,

            statusMessage = forceUpdateStatus,

            needsInstallPermission = needsInstallPermission,

            onOpenSettings = {

                if (activity != null) {

                    updateManager.requestInstallPermission(activity)

                    scope.launch {

                        delay(1500)

                        if (updateManager.canInstallPackages()) {

                            needsInstallPermission = false

                            performUpdate(pendingUpdate!!)

                        }

                    }

                }

            },

        )

        return

    }



    LaunchedEffect(startupAttempt) {

        initPhase = AppInitPhase.Preparing

        initError = null

        syncProgress = SyncProgress("Preparing app...")

        delay(300)

        try {

            val validation = withContext(Dispatchers.IO) {

                StartupValidator.run(context, credentials)

            }

            validation.log()

            if (!validation.ok) {

                throw SyncException(validation.checks.lastOrNull { it.startsWith("FAIL:") }

                    ?.removePrefix("FAIL: ")

                    ?: "Startup validation failed.")

            }



            val repo = withContext(Dispatchers.IO) {

                val instance = LocalMediaRepository.getInstance(context)

                if (!instance.verifyDatabaseHealth()) {

                    throw SyncException("Local database failed health check.")

                }

                instance

            }

            repository = repo

            val cached = withContext(Dispatchers.IO) { repo.getSummary() }

            summary = cached

            initPhase = AppInitPhase.Ready

            val isEmpty = cached.liveCount + cached.movieCount + cached.plexCount == 0

            if (isEmpty) {

                delay(400)

                loadCatalog(liveOnly = true)

            }

        } catch (e: Exception) {

            val msg = e.message ?: "Failed to start app."

            initError = msg

            initPhase = AppInitPhase.Failed

            AppDiagnostics.recordError(context, msg)

        }

    }



    when (initPhase) {

        AppInitPhase.Preparing -> {

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .background(ThemeColors.DarkBackground)

                    .padding(24.dp),

                contentAlignment = Alignment.Center,

            ) {

                SyncProgressView(progress = syncProgress)

            }

            return

        }

        AppInitPhase.Failed -> {

            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .background(ThemeColors.DarkBackground)

                    .padding(24.dp),

                contentAlignment = Alignment.Center,

            ) {

                SyncErrorView(

                    message = initError ?: "Failed to start app.",

                    onRetry = { startupAttempt++ },

                )

            }

            return

        }

        AppInitPhase.Ready -> Unit

    }



    val repo = repository ?: return



    Box(modifier = Modifier.fillMaxSize().background(ThemeColors.DarkBackground)) {

        Row(modifier = Modifier.fillMaxSize()) {

            PlexSidebar(

                current = currentScreen,

                onNavigate = { currentScreen = it },

            )

            Column(

                modifier = Modifier

                    .weight(1f)

                    .fillMaxSize()

                    .padding(horizontal = 20.dp, vertical = 16.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp),

            ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically,

            ) {

                Column {

                    Text(

                        text = "v${BuildConfig.VERSION_NAME}",

                        style = MaterialTheme.typography.labelMedium,

                        color = ThemeColors.TextMuted,

                    )

                    if (credentials.isDemoMode) {

                        Text(

                            text = "Demo mode — sample catalog",

                            style = MaterialTheme.typography.bodySmall,

                            color = ThemeColors.AccentPrimary,

                        )

                    }

                }



                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    Button(onClick = { loadCatalog(showRefreshIndicator = true) }, enabled = !isRefreshing) {

                        Text(if (isRefreshing) "Syncing..." else "↻ Sync")

                    }

                    Button(

                        onClick = {

                            onTriggerVoiceSearch { spokenText ->

                                searchQuery = spokenText

                                scope.launch {

                                    searchResults = repo.search(spokenText)

                                }

                                currentScreen = AppScreen.HOME

                            }

                        },

                        modifier = Modifier

                            .border(2.dp, activeAccentColor, RoundedCornerShape(4.dp))

                            .padding(horizontal = 8.dp),

                    ) {

                        Text("🎤 Voice", color = ThemeColors.TextPrimary)

                    }

                }

            }



            if (isSyncing) {

                SyncProgressView(

                    progress = syncProgress,

                    modifier = Modifier.fillMaxWidth(),

                )

            }



            errorMessage?.let { message ->

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically,

                ) {

                    Text(

                        text = "Sync failed: $message",

                        style = MaterialTheme.typography.bodySmall,

                        color = ThemeColors.CrimsonAccent,

                        modifier = Modifier.weight(1f),

                    )

                    Button(onClick = { loadCatalog(showRefreshIndicator = true) }) {

                        Text("Retry")

                    }

                }

            }



            val catalog = summary ?: CatalogSummary()



            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                if (!isSyncing && catalog.liveCount + catalog.movieCount + catalog.plexCount == 0) {

                    Column(

                        modifier = Modifier.fillMaxSize(),

                        verticalArrangement = Arrangement.Center,

                        horizontalAlignment = Alignment.CenterHorizontally,

                    ) {

                        Text(

                            text = "No catalog loaded yet.",

                            style = MaterialTheme.typography.titleMedium,

                            color = ThemeColors.TextPrimary,

                        )

                        Button(

                            onClick = { loadCatalog(showRefreshIndicator = true) },

                            modifier = Modifier.padding(top = 16.dp),

                        ) {

                            Text("Sync Catalog")

                        }

                    }

                } else {

                when (currentScreen) {

                    AppScreen.HOME -> HomeScreen(

                        summary = catalog,

                        repository = repo,

                        epgRepository = epgRepository,

                        credentials = credentials,

                        searchQuery = searchQuery,

                        searchResults = searchResults,

                        onPlay = ::playItem,

                    )

                    AppScreen.LIVE_TV -> LiveTvGuideScreen(

                        summary = catalog,

                        repository = repo,

                        epgRepository = epgRepository,

                        onPlay = ::playItem,

                    )

                    AppScreen.GUIDE -> TvGuideScreen(

                        repository = repo,

                        epgRepository = epgRepository,

                        onPlay = ::playItem,

                    )

                    AppScreen.MOVIES -> MoviesBrowserScreen(

                        summary = catalog,

                        repository = repo,

                        onPlay = ::playItem,

                    )

                    AppScreen.TV_SHOWS -> TvShowsBrowserScreen(

                        summary = catalog,

                        repository = repo,

                        onPlay = ::playItem,

                    )

                    AppScreen.SETTINGS -> SettingsScreen(

                        playerSettings = playerSettings,

                        credentials = credentials,

                        onResync = { loadCatalog(showRefreshIndicator = true) },

                        onUpdateChecked = { result ->

                            when (result) {

                                is UpdateCheckResult.UpdateAvailable -> {

                                    beginForceUpdate(result.info)

                                }

                                is UpdateCheckResult.UpToDate -> Unit

                                is UpdateCheckResult.Error -> Unit

                            }

                        },

                    )

                }

                }

            }

        }

    }

}

}



@Composable
private fun HomeScreen(
    summary: CatalogSummary,
    repository: LocalMediaRepository,
    epgRepository: EpgRepository,
    credentials: CredentialStore,
    searchQuery: String,
    searchResults: SearchResult?,
    onPlay: (MediaItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val trendingRepo = remember { TrendingRepository.getInstance(context) }
    val contentResolver = remember { TrendingContentResolver.getInstance(context) }
    var favorites by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var featured by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var recentMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tmdbRows by remember { mutableStateOf(TmdbHomeRows()) }
    var selectedCategoryId by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        favorites = repository.getFavorites(16)
        featured = repository.getFeaturedLive(14)
        recentMovies = repository.getItemsBySource(MediaSource.XTREAM_VOD, limit = 20)
        tmdbRows = trendingRepo.getHomeRows()
        EpgSyncService.start(context, force = false)
        epgRepository.ensureXmltvParsed()
    }

    LaunchedEffect(featured) {
        if (featured.isEmpty()) return@LaunchedEffect
        val epg = mutableMapOf<String, String>()
        val now = System.currentTimeMillis() / 1000
        featured.take(8).forEach { channel ->
            epgRepository.getOrFetchEpg(channel).firstOrNull {
                it.startEpochSec <= now && it.endEpochSec > now
            }?.let { epg[channel.playbackId] = it.title }
        }
        nowPlaying = epg
    }

    val heroTmdb = tmdbRows.heroItem
    val heroChannel = featured.firstOrNull()
    val heroTitle = heroTmdb?.displayTitle ?: heroChannel?.title ?: "Welcome to Rushy"
    val heroSubtitle = heroTmdb?.overview?.take(120) ?: heroChannel?.let { nowPlaying[it.playbackId] }
    val heroBackdrop = heroTmdb?.backdropUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (searchQuery.isNotBlank()) {
            Text(
                text = "Results for \"$searchQuery\"",
                color = ThemeColors.AccentPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            searchResults?.exactMatches?.takeIf { it.isNotEmpty() }?.let { matches ->
                ThumbnailMediaRow(title = "Exact Matches", items = matches, onItemClick = onPlay)
            }
            searchResults?.nearMatches?.takeIf { it.isNotEmpty() }?.let { matches ->
                ThumbnailMediaRow(title = "Near Matches", items = matches, onItemClick = onPlay)
            }
        }

        PlexHeroBanner(
            title = heroTitle,
            subtitle = heroSubtitle,
            backdropUrl = heroBackdrop,
            onPlay = {
                when {
                    heroChannel != null -> onPlay(heroChannel)
                    heroTmdb != null -> scope.launch {
                        val state = contentResolver.resolveAction(heroTmdb, repository)
                        state.playableItem?.let { onPlay(it) }
                            ?: run {
                                if (credentials.hasPlexCredentials()) {
                                    val result = contentResolver.requestOnPlex(heroTmdb)
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
            },
        )

        if (!credentials.hasPlexCredentials()) {
            Text(
                text = "Connect Plex in Settings to request trending content",
                color = ThemeColors.AccentPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        tmdbRows.error?.let { err ->
            Text(text = err, color = ThemeColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }

        TrendingActionRow(
            title = "Trending Movies",
            items = tmdbRows.trendingMoviesDay,
            repository = repository,
            credentials = credentials,
            onPlay = onPlay,
        )

        TrendingActionRow(
            title = "Trending TV Shows",
            items = tmdbRows.trendingTvDay,
            repository = repository,
            credentials = credentials,
            onPlay = onPlay,
        )

        TrendingActionRow(
            title = "Popular Movies",
            items = tmdbRows.popularMovies,
            repository = repository,
            credentials = credentials,
            onPlay = onPlay,
        )

        TrendingActionRow(
            title = "Popular TV Shows",
            items = tmdbRows.topRatedMovies,
            repository = repository,
            credentials = credentials,
            onPlay = onPlay,
        )

        if (favorites.isNotEmpty()) {
            HeroChannelRow(title = "Live TV Favorites", items = favorites, onPlay = onPlay, nowPlaying = nowPlaying)
        }

        HeroChannelRow(
            title = "Live TV",
            items = featured,
            onPlay = onPlay,
            nowPlaying = nowPlaying,
        )

        if (recentMovies.isNotEmpty()) {
            ThumbnailMediaRow(
                title = "Recently Added Movies",
                items = recentMovies,
                onItemClick = onPlay,
            )
        }

        Text(
            text = "Browse by Category",
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        CategoryPillRow(
            categories = summary.liveCategories,
            selectedId = selectedCategoryId,
            onSelect = { selectedCategoryId = it },
        )

        Text(
            text = "${summary.liveCount} channels · ${summary.movieCount} titles",
            color = ThemeColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}



@Composable

private fun ThumbnailMediaRow(

    title: String,

    items: List<MediaItem>,

    emptyLabel: String? = null,

    onItemClick: (MediaItem) -> Unit,

) {

    Text(

        text = title,

        style = MaterialTheme.typography.titleMedium,

        color = ThemeColors.TextPrimary,

    )



    if (items.isEmpty()) {

        emptyLabel?.let { Text(text = it, color = ThemeColors.CobaltAccent) }

        return

    }



    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

        items(items, key = { it.id }) { item ->

            Button(onClick = { onItemClick(item) }) {

                PosterCard(item = item)

            }

        }

    }

}


