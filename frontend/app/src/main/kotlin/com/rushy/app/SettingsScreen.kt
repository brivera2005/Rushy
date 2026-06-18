package com.rushy.app

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    playerSettings: PlayerSettings,
    credentials: CredentialStore,
    onResync: () -> Unit,
    onUpdateChecked: (UpdateCheckResult) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val updatePrefs = remember { UpdatePreferences.getInstance(context) }
    val updateManager = remember { ApkUpdateManager(context) }
    val accentColor = LocalRushyTheme.current.currentAccentColor

    var autoUpdate by remember { mutableStateOf(updatePrefs.autoUpdateEnabled) }
    var checkOnStartup by remember { mutableStateOf(updatePrefs.checkOnStartup) }
    var isChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = ThemeColors.TextPrimary,
        )

        UpdatesSection(
            autoUpdate = autoUpdate,
            checkOnStartup = checkOnStartup,
            isChecking = isChecking,
            updateStatus = updateStatus,
            accentColor = accentColor,
            canInstallPackages = updateManager.canInstallPackages(),
            onAutoUpdateToggle = {
                autoUpdate = !autoUpdate
                updatePrefs.autoUpdateEnabled = autoUpdate
            },
            onCheckOnStartupToggle = {
                checkOnStartup = !checkOnStartup
                updatePrefs.checkOnStartup = checkOnStartup
            },
            onCheckForUpdates = {
                scope.launch {
                    isChecking = true
                    updateStatus = "Checking for updates..."
                    val result = updateManager.checkForUpdate()
                    isChecking = false
                    onUpdateChecked(result)
                    updateStatus = when (result) {
                        is UpdateCheckResult.UpToDate ->
                            "You are on the latest version (v${BuildConfig.VERSION_NAME})."
                        is UpdateCheckResult.UpdateAvailable ->
                            "Update found: v${result.info.versionName} — installing automatically..."
                        is UpdateCheckResult.Error -> result.message
                    }
                }
            },
            onRequestInstallPermission = {
                if (activity != null) {
                    updateManager.requestInstallPermission(activity)
                }
            },
        )

        Text(
            text = "Default players per content type. Built-in player works without external apps.",
            color = ThemeColors.CobaltAccent,
        )

        ContentType.entries.forEach { contentType ->
            PlayerPickerSection(
                label = contentType.label,
                selected = playerSettings.getPlayer(contentType),
                onSelect = { playerSettings.setPlayer(contentType, it) },
                installedCheck = { player ->
                    player.packageName?.let { pkg ->
                        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
                    } ?: true
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Xtream Portal",
                style = MaterialTheme.typography.titleMedium,
                color = ThemeColors.TextPrimary,
            )
            Text(
                text = if (credentials.hasXtreamCredentials()) {
                    "Connected: ${credentials.xtreamPortal} (${credentials.xtreamUsername})"
                } else {
                    "No Xtream credentials configured."
                },
                color = ThemeColors.TextSecondary,
            )
            if (credentials.isDemoMode) {
                Text(text = "Running in demo mode.", color = ThemeColors.CrimsonAccent)
            }
        }

        PlexWatchlistSettingsSection(credentials = credentials)

        TraktSettingsSection()

        Button(onClick = onResync) {
            Text("Refresh Catalog")
        }

        AppDiagnostics.lastError(context)?.let { lastError ->
            Text(
                text = "Last error: $lastError",
                color = ThemeColors.CrimsonAccent,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AppDiagnostics.readCrashLogTail(context)?.let { crashTail ->
            Text(
                text = "Recent crash log:\n$crashTail",
                color = ThemeColors.CobaltAccent.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = "Tip: Set Live TV to Built-in Player if TiviMate is not installed.",
            color = ThemeColors.TextPrimary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TraktSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val traktSettings = remember { TraktSettings.getInstance(context) }
    val traktClient = remember { TraktApiClient(traktSettings) }

    var clientId by remember { mutableStateOf(traktSettings.clientIdOverride) }
    var clientSecret by remember { mutableStateOf(traktSettings.clientSecretOverride) }
    var status by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var pendingSession by remember { mutableStateOf(traktSettings.pendingSessionOrNull()) }
    var isConnected by remember { mutableStateOf(traktSettings.isConnected()) }

    val buildClientId = BuildConfig.TRAKT_CLIENT_ID.isNotBlank()
    val buildClientSecret = BuildConfig.TRAKT_CLIENT_SECRET.isNotBlank()

    LaunchedEffect(Unit) {
        if (traktSettings.hasPendingDeviceAuth() && traktSettings.hasClientCredentials()) {
            isConnecting = true
            status = "Resuming Trakt authorization..."
            when (val result = traktClient.pollDeviceTokenUntilComplete()) {
                TraktPollResult.Success -> {
                    isConnected = true
                    pendingSession = null
                    status = "Trakt account connected"
                }
                TraktPollResult.Expired -> {
                    pendingSession = null
                    status = "Trakt code expired — tap Connect Trakt Account"
                }
                is TraktPollResult.Error -> status = result.message
                TraktPollResult.Pending -> Unit
            }
            isConnecting = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Trakt",
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        Text(
            text = "Trending and discover rows use Trakt. Public lists need only a Client ID; linking your account enables personalized data.",
            color = ThemeColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = when {
                isConnected -> "Account: Connected"
                traktSettings.hasClientId() -> "Account: Not linked (public trending active)"
                else -> "Client ID not configured"
            },
            color = if (isConnected) ThemeColors.EmeraldAccent else ThemeColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        if (!buildClientId) {
            SettingsTextField(
                label = "Trakt Client ID",
                value = clientId,
                placeholder = "From trakt.tv/oauth/applications",
                onValueChange = { clientId = it },
            )
        } else {
            Text(
                text = "Client ID: configured in build",
                color = ThemeColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!buildClientSecret) {
            SettingsTextField(
                label = "Trakt Client Secret",
                value = clientSecret,
                placeholder = "Required for account linking",
                onValueChange = { clientSecret = it },
            )
        } else {
            Text(
                text = "Client Secret: configured in build",
                color = ThemeColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!buildClientId || !buildClientSecret) {
                Button(
                    onClick = {
                        traktSettings.clientIdOverride = clientId
                        traktSettings.clientSecretOverride = clientSecret
                        status = "Trakt API credentials saved"
                        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Save Trakt API")
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        isConnecting = true
                        status = "Starting Trakt device auth..."
                        val result = traktClient.startDeviceAuth()
                        isConnecting = false
                        result.fold(
                            onSuccess = { session ->
                                pendingSession = session
                                status = "Enter code at ${session.verificationUrl}"
                                Toast.makeText(
                                    context,
                                    "Enter ${session.userCode} at trakt.tv/activate",
                                    Toast.LENGTH_LONG,
                                ).show()
                                isConnecting = true
                                when (val poll = traktClient.pollDeviceTokenUntilComplete()) {
                                    TraktPollResult.Success -> {
                                        isConnected = true
                                        pendingSession = null
                                        status = "Trakt account connected"
                                    }
                                    TraktPollResult.Expired -> {
                                        pendingSession = null
                                        status = "Code expired — try again"
                                    }
                                    is TraktPollResult.Error -> status = poll.message
                                    TraktPollResult.Pending -> Unit
                                }
                                isConnecting = false
                            },
                            onFailure = { err ->
                                status = err.message ?: "Failed to start Trakt auth"
                                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                            },
                        )
                    }
                },
                enabled = !isConnecting && traktSettings.hasClientCredentials(),
            ) {
                Text(if (isConnecting) "Connecting..." else "Connect Trakt Account")
            }
            if (isConnected) {
                Button(
                    onClick = {
                        traktSettings.disconnect()
                        isConnected = false
                        pendingSession = null
                        status = "Trakt disconnected"
                    },
                ) {
                    Text("Disconnect")
                }
            }
        }

        pendingSession?.let { session ->
            Text(
                text = "Go to ${session.verificationUrl} and enter: ${session.userCode}",
                color = ThemeColors.AccentPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        status?.let {
            Text(
                text = it,
                color = ThemeColors.CobaltAccent,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlexWatchlistSettingsSection(credentials: CredentialStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(credentials.plexServerUrl) }
    var token by remember { mutableStateOf(credentials.plexToken) }
    var status by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Connect Plex for Watchlist Requests",
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        Text(
            text = "Add your Plex server URL and X-Plex-Token to request trending movies and shows. Your Plex server (Radarr/Sonarr) handles downloads.",
            color = ThemeColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        SettingsTextField(
            label = "Plex Server URL",
            value = serverUrl,
            placeholder = "http://192.168.1.10:32400",
            onValueChange = { serverUrl = it },
        )
        SettingsTextField(
            label = "X-Plex-Token",
            value = token,
            placeholder = "Your Plex token",
            onValueChange = { token = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    credentials.savePlex(serverUrl, token)
                    status = "Plex credentials saved"
                    Toast.makeText(context, "Plex credentials saved", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Save Plex")
            }
            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        status = "Testing Plex connection..."
                        val ok = TrendingContentResolver.getInstance(context).testPlexConnection()
                        isTesting = false
                        status = if (ok) {
                            "Plex watchlist connected ✓"
                        } else {
                            "Plex connection failed — check token"
                        }
                        Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isTesting && token.isNotBlank(),
            ) {
                Text(if (isTesting) "Testing..." else "Test Connection")
            }
        }

        status?.let {
            Text(
                text = it,
                color = if (it.contains("✓")) ThemeColors.EmeraldAccent else ThemeColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = ThemeColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = ThemeColors.TextPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .background(ThemeColors.SurfaceDark)
                .padding(12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, color = ThemeColors.TextMuted)
                }
                inner()
            },
        )
    }
}

@Composable
private fun UpdatesSection(
    autoUpdate: Boolean,
    checkOnStartup: Boolean,
    isChecking: Boolean,
    updateStatus: String?,
    accentColor: androidx.compose.ui.graphics.Color,
    canInstallPackages: Boolean,
    onAutoUpdateToggle: () -> Unit,
    onCheckOnStartupToggle: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onRequestInstallPermission: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Updates",
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        Text(
            text = "Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            color = ThemeColors.TextPrimary.copy(alpha = 0.85f),
        )
        Text(
            text = "GitHub: ${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPO}",
            color = ThemeColors.TextPrimary.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = onCheckForUpdates, enabled = !isChecking) {
            Text(if (isChecking) "Checking..." else "Check for Updates")
        }

        SettingsToggleRow(
            label = "Auto-update when available",
            enabled = autoUpdate,
            accentColor = accentColor,
            onToggle = onAutoUpdateToggle,
        )
        SettingsToggleRow(
            label = "Check for updates on startup",
            enabled = checkOnStartup,
            accentColor = accentColor,
            onToggle = onCheckOnStartupToggle,
        )

        if (!canInstallPackages) {
            Text(
                text = "Install permission is required to apply updates.",
                color = ThemeColors.CrimsonAccent,
            )
            Button(onClick = onRequestInstallPermission) {
                Text("Allow App Installs")
            }
        }

        updateStatus?.let { status ->
            Text(text = status, color = ThemeColors.CobaltAccent)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    enabled: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text = label, color = ThemeColors.TextPrimary, modifier = Modifier.weight(1f))
        Button(
            onClick = onToggle,
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = if (enabled) accentColor else ThemeColors.TextPrimary.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 4.dp),
        ) {
            Text(if (enabled) "On" else "Off", color = ThemeColors.TextPrimary)
        }
    }
}

@Composable
private fun PlayerPickerSection(
    label: String,
    selected: PlayerType,
    onSelect: (PlayerType) -> Unit,
    installedCheck: (PlayerType) -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = ThemeColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerType.entries.forEach { player ->
                val installed = installedCheck(player)
                val isSelected = player == selected
                Button(
                    onClick = { if (installed) onSelect(player) },
                    modifier = if (isSelected) {
                        Modifier.border(
                            2.dp,
                            LocalRushyTheme.current.currentAccentColor,
                            MaterialTheme.shapes.small,
                        )
                    } else {
                        Modifier
                    },
                ) {
                    val suffix = if (!installed && player.packageName != null) " (not installed)" else ""
                    Text(
                        text = player.label + suffix,
                        color = if (installed) ThemeColors.TextPrimary else ThemeColors.CobaltAccent.copy(0.5f),
                    )
                }
            }
        }
    }
}
