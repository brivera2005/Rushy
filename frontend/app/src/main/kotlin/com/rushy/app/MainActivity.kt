package com.rushy.app



import android.util.Log

import android.app.Activity

import android.content.Intent

import android.os.Bundle

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

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.runtime.Composable

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import androidx.tv.foundation.lazy.list.TvLazyRow

import androidx.tv.foundation.lazy.list.items

import androidx.tv.material3.Button

import androidx.tv.material3.MaterialTheme

import androidx.tv.material3.Surface

import androidx.tv.material3.Text

import kotlinx.coroutines.CoroutineExceptionHandler

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import kotlinx.coroutines.plus

import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {

    private var onSpeechResultReceived: ((String) -> Unit)? = null



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

    var showUpdateDialog by remember { mutableStateOf(false) }

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

            updateManager.requestInstallPermission(activity)

            return

        }

        isDownloadingUpdate = true

        downloadProgress = 0

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

                Toast.makeText(context, download.message, Toast.LENGTH_LONG).show()

            }

        }

    }



    fun handleUpdateCheckResult(result: UpdateCheckResult, autoInstall: Boolean) {

        when (result) {

            is UpdateCheckResult.UpdateAvailable -> {

                pendingUpdate = result.info

                if (autoInstall && updatePrefs.autoUpdateEnabled) {

                    scope.launch { performUpdate(result.info) }

                } else {

                    showUpdateDialog = true

                }

            }

            is UpdateCheckResult.UpToDate -> {

                pendingUpdate = null

                showUpdateDialog = false

            }

            is UpdateCheckResult.Error -> {

                if (!autoInstall) {

                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()

                }

            }

        }

    }



    fun checkForUpdates(autoInstall: Boolean) {

        if (isCheckingUpdate) return

        scope.launch {

            isCheckingUpdate = true

            val result = updateManager.checkForUpdate()

            isCheckingUpdate = false

            handleUpdateCheckResult(result, autoInstall = autoInstall)

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

            }

        }

    }



    fun playItem(item: MediaItem) {

        playback.play(item)

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

            if (updatePrefs.checkOnStartup) {

                delay(1200)

                checkForUpdates(autoInstall = true)

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



    Box(modifier = Modifier.fillMaxSize()) {

        Column(

            modifier = Modifier

                .fillMaxSize()

                .background(ThemeColors.DarkBackground)

                .padding(24.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp),

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically,

            ) {

                Column {

                    RushyLogo(size = RushyLogoSize.Header, showTagline = !credentials.isDemoMode)

                    if (credentials.isDemoMode) {

                        Text(

                            text = "Demo mode — sample catalog",

                            style = MaterialTheme.typography.bodySmall,

                            color = ThemeColors.CobaltAccent,

                            modifier = Modifier.padding(top = 4.dp),

                        )

                    }

                }



                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    pendingUpdate?.let { update ->

                        Button(

                            onClick = { showUpdateDialog = true },

                            modifier = Modifier

                                .border(2.dp, ThemeColors.EmeraldAccent, MaterialTheme.shapes.small)

                                .padding(horizontal = 8.dp),

                        ) {

                            Text(

                                text = "Update Available (v${update.versionName})",

                                color = ThemeColors.EmeraldAccent,

                            )

                        }

                    }

                    Button(onClick = { loadCatalog(showRefreshIndicator = true) }, enabled = !isRefreshing) {

                        Text(if (isRefreshing) "Refreshing..." else "Refresh")

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

                            .border(2.dp, activeAccentColor, MaterialTheme.shapes.small)

                            .padding(horizontal = 8.dp),

                    ) {

                        Text("Voice Search", color = ThemeColors.TextPrimary)

                    }

                }

            }



            AppNavBar(

                current = currentScreen,

                onNavigate = { currentScreen = it },

                settingsHasUpdate = pendingUpdate != null,

            )



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

                        searchQuery = searchQuery,

                        searchResults = searchResults,

                        onPlay = ::playItem,

                    )

                    AppScreen.LIVE_TV -> ChannelBrowserScreen(

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

                    AppScreen.SETTINGS -> SettingsScreen(

                        playerSettings = playerSettings,

                        credentials = credentials,

                        onResync = { loadCatalog(showRefreshIndicator = true) },

                        pendingUpdate = pendingUpdate,

                        onUpdateChecked = { result ->

                            handleUpdateCheckResult(result, autoInstall = false)

                        },

                        onStartUpdate = { update ->

                            scope.launch { performUpdate(update) }

                        },

                    )

                }

                }

            }

        }



        if (showUpdateDialog && pendingUpdate != null) {

            UpdateAvailableDialog(

                updateInfo = pendingUpdate!!,

                isDownloading = isDownloadingUpdate,

                downloadProgress = downloadProgress,

                onUpdateNow = { scope.launch { performUpdate(pendingUpdate!!) } },

                onLater = { showUpdateDialog = false },

            )

        }

    }

}



@Composable

private fun HomeScreen(

    summary: CatalogSummary,

    repository: LocalMediaRepository,

    searchQuery: String,

    searchResults: SearchResult?,

    onPlay: (MediaItem) -> Unit,

) {

    var favorites by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var featured by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var previewMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }



    LaunchedEffect(Unit) {

        favorites = repository.getFavorites(16)

        featured = repository.getFeaturedLive(12)

        previewMovies = repository.getItemsBySource(MediaSource.XTREAM_VOD, limit = 16)

    }



    Column(

        modifier = Modifier

            .fillMaxSize()

            .verticalScroll(rememberScrollState()),

        verticalArrangement = Arrangement.spacedBy(20.dp),

    ) {

        if (searchQuery.isNotBlank()) {

            Text(

                text = "Results for \"$searchQuery\"",

                color = LocalRushyTheme.current.currentAccentColor,

            )

            searchResults?.exactMatches?.takeIf { it.isNotEmpty() }?.let { matches ->

                ThumbnailMediaRow(title = "Exact Matches", items = matches, onItemClick = onPlay)

            }

            searchResults?.nearMatches?.takeIf { it.isNotEmpty() }?.let { matches ->

                ThumbnailMediaRow(title = "Near Matches", items = matches, onItemClick = onPlay)

            }

        }



        if (favorites.isNotEmpty()) {

            HeroChannelRow(title = "Your Favorites", items = favorites, onPlay = onPlay)

        }



        HeroChannelRow(

            title = "Live TV (${summary.liveCount})",

            items = featured,

            onPlay = onPlay,

        )



        ThumbnailMediaRow(

            title = "Movies & Series (${summary.movieCount})",

            items = previewMovies,

            onItemClick = onPlay,

        )



        Text(

            text = "Browse ${summary.liveCategories.size - 1} channel categories and ${summary.movieCount} titles",

            color = ThemeColors.CobaltAccent,

            style = MaterialTheme.typography.bodyMedium,

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



    TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        items(items, key = { it.id }) { item ->

            Button(onClick = { onItemClick(item) }) {

                MediaThumbnail(item = item, modifier = Modifier.width(140.dp))

            }

        }

    }

}


