package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Full-screen blocking UI shown during forced OTA updates.
 * Replaces the entire app — no background UI is composed, no D-pad interaction needed.
 */
@Composable
fun ForceUpdateScreen(
    updateInfo: UpdateInfo,
    downloadProgress: Int,
    statusMessage: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground)
            .onPreviewKeyEvent { true },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .background(ThemeColors.SurfaceElevated)
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Updating Rushy",
                style = MaterialTheme.typography.headlineMedium,
                color = ThemeColors.TextPrimary,
            )
            Text(
                text = "Installing v${updateInfo.versionName} (build ${updateInfo.versionCode})",
                color = ThemeColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Current: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = ThemeColors.TextSecondary,
            )

            val progressLabel = when {
                statusMessage != null -> statusMessage
                downloadProgress in 0..100 -> "Downloading... $downloadProgress%"
                else -> "Downloading update..."
            }
            Text(
                text = progressLabel,
                color = ThemeColors.AccentPrimary,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Please wait — the installer will open automatically.",
                color = ThemeColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Optional chooser for manual "Check for Updates" in Settings only.
 * Uses early-return placement in MainActivity so no background UI exists.
 */
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
) {
    val updateFocus = remember { FocusRequester() }
    val laterFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(8) { attempt ->
            delay(80L * (attempt + 1))
            try {
                updateFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                // Button not attached yet — retry
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    if (!isDownloading) onLater()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .focusProperties { canFocus = false }
                .background(ThemeColors.SurfaceElevated)
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.headlineMedium,
                color = ThemeColors.TextPrimary,
            )
            Text(
                text = "Version ${updateInfo.versionName} (build ${updateInfo.versionCode})",
                color = ThemeColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = ThemeColors.TextSecondary,
            )

            if (updateInfo.changelog.isNotBlank()) {
                Text(
                    text = "What's new",
                    style = MaterialTheme.typography.titleMedium,
                    color = ThemeColors.TextPrimary,
                )
                Text(
                    text = updateInfo.changelog,
                    color = ThemeColors.TextSecondary,
                )
            }

            if (isDownloading) {
                val progressLabel = if (downloadProgress in 0..100) {
                    "Downloading... $downloadProgress%"
                } else {
                    "Downloading..."
                }
                Text(text = progressLabel, color = ThemeColors.TextPrimary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .focusRequester(updateFocus)
                        .focusProperties { down = updateFocus; up = updateFocus },
                ) {
                    Text(
                        "Update Now",
                        color = ThemeColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Button(
                    onClick = onLater,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .focusRequester(laterFocus)
                        .focusProperties { down = laterFocus; up = laterFocus },
                ) {
                    Text(
                        "Later",
                        color = ThemeColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Text(
                text = "Use ◀ ▶ to choose Update Now or Later, then OK.",
                color = ThemeColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
