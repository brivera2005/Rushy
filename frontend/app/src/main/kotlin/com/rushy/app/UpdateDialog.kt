package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Full-screen blocking UI during forced OTA updates.
 * No background UI is composed. During download there are zero focusable controls.
 * When install permission is missing, exactly one "Open Settings" button is shown.
 */
@Composable
fun ForceUpdateScreen(
    updateInfo: UpdateInfo,
    downloadProgress: Int,
    statusMessage: String? = null,
    needsInstallPermission: Boolean = false,
    onOpenSettings: () -> Unit = {},
) {
    val settingsFocus = remember { FocusRequester() }

    LaunchedEffect(needsInstallPermission) {
        if (!needsInstallPermission) return@LaunchedEffect
        repeat(8) { attempt ->
            delay(80L * (attempt + 1))
            try {
                settingsFocus.requestFocus()
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
            .then(
                if (!needsInstallPermission) {
                    Modifier.onPreviewKeyEvent { true }
                } else {
                    Modifier
                },
            ),
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
                text = if (needsInstallPermission) "Permission Required" else "Updating Rushy",
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
                needsInstallPermission ->
                    "Allow installs from this app, then return to Rushy — the update will continue automatically."
                downloadProgress in 0..100 -> "Downloading... $downloadProgress%"
                else -> "Downloading update..."
            }
            Text(
                text = progressLabel,
                color = ThemeColors.AccentPrimary,
                style = MaterialTheme.typography.titleMedium,
            )

            if (needsInstallPermission) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(52.dp)
                        .focusRequester(settingsFocus)
                        .focusGroup(),
                ) {
                    Text(
                        text = "Open Settings",
                        color = ThemeColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Text(
                    text = "Please wait — the installer will open automatically.",
                    color = ThemeColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
