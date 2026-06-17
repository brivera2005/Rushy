package com.rushy.app

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            val credentials = remember { CredentialStore.getInstance(context) }
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
                                RushyLogo(
                                    size = RushyLogoSize.Splash,
                                    showTagline = true,
                                )
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

@Composable
fun RushyApp(
    onTriggerVoiceSearch: ((String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val repository = remember { LocalMediaRepository.getInstance(context) }
    val credentials = remember { CredentialStore.getInstance(context) }
    val playerSettings = remember { PlayerSettings.getInstance(context) }
    val epgRepository = remember { EpgRepository.getInstance(context) }
    val playback = remember { PlaybackRouter.getInstance(context) }
    val updatePrefs = remember { UpdatePreferences.getInstance(context) }
    val updateManager = remember { ApkUpdateManager(context) }

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var dashboard by remember { mutableStateOf<DashboardData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<SearchResult?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    val activeAccentColor = LocalRushyTheme.current.currentAccentColor

    suspend fun performUpdate(updateInfo: UpdateInfo) {
        if (activity != null && !updateManager.canInstallPackages()) {
            updateManager.requestInstallPermission(activity)
            return
        }

        isDownloadingUpdate = true
        downloadProgress = 0
        when (val download = updateManager.downloadApk(updateInfo) { progress ->
            downloadProgress = progress
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

    fun loadCatalog(showRefreshIndicator: Boolean = false) {
        scope.launch {
            if (showRefreshIndicator) isRefreshing = true else isLoading = true
            errorMessage = null
            try {
                dashboard = repository.syncCatalog()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Sync failed."
                dashboard = repository.getDashboard()
                if (dashboard?.liveTv.isNullOrEmpty() && dashboard?.movies.isNullOrEmpty()) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun playItem(item: MediaItem) {
        playback.play(item)
    }

    LaunchedEffect(Unit) {
        loadCatalog()
        if (updatePrefs.checkOnStartup) {
            delay(1200)
            checkForUpdates(autoInstall = true)
        }
    }

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
                RushyLogo(
                    size = RushyLogoSize.Header,
                    showTagline = !credentials.isDemoMode,
                )
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
                Button(
                    onClick = { loadCatalog(showRefreshIndicator = true) },
                    enabled = !isRefreshing,
                ) {
                    Text(if (isRefreshing) "Refreshing..." else "Refresh")
                }
                Button(
                    onClick = {
                        onTriggerVoiceSearch { spokenText ->
                            searchQuery = spokenText
                            searchResults = repository.search(spokenText)
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

        if (isLoading) {
            RushyLoadingView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                message = "Loading catalog...",
            )
            return@Column
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ThemeColors.CrimsonAccent,
            )
        }

        val data = dashboard ?: return@Column

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    data = data,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onPlay = ::playItem,
                )
                AppScreen.LIVE_TV -> ChannelBrowserScreen(
                    dashboard = data,
                    repository = repository,
                    onPlay = ::playItem,
                    onDataChanged = { dashboard = repository.getDashboard() },
                )
                AppScreen.GUIDE -> TvGuideScreen(
                    channels = data.liveTv,
                    epgRepository = epgRepository,
                    onPlay = ::playItem,
                )
                AppScreen.MOVIES -> VodBrowserScreen(
                    items = data.movies,
                    title = "Movies & Series",
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

        if (showUpdateDialog && pendingUpdate != null) {
            UpdateAvailableDialog(
                updateInfo = pendingUpdate!!,
                isDownloading = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                onUpdateNow = {
                    scope.launch { performUpdate(pendingUpdate!!) }
                },
                onLater = { showUpdateDialog = false },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    data: DashboardData,
    searchQuery: String,
    searchResults: SearchResult?,
    onPlay: (MediaItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
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

        if (data.favorites.isNotEmpty()) {
            ThumbnailMediaRow(title = "Favorites", items = data.favorites, onItemClick = onPlay)
        }

        ThumbnailMediaRow(
            title = "Live TV (${data.liveTv.size})",
            items = data.liveTv.take(20),
            onItemClick = onPlay,
        )

        ThumbnailMediaRow(
            title = "Movies & Series (${data.movies.size})",
            items = data.movies.take(20),
            onItemClick = onPlay,
        )

        ThumbnailMediaRow(
            title = "Plex Library",
            items = data.plexLibrary.take(20),
            emptyLabel = "No Plex items cached yet.",
            onItemClick = onPlay,
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
        emptyLabel?.let {
            Text(text = it, color = ThemeColors.CobaltAccent)
        }
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
