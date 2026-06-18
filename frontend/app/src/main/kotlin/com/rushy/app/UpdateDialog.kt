package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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
        updateFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .background(ThemeColors.DarkBackground)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    if (!isDownloading) {
                        onLater()
                        true
                    } else {
                        true
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .tvFocusHighlight(focused = true)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .focusRequester(updateFocus),
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
                        .focusRequester(laterFocus),
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
