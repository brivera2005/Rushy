package com.rushy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .border(2.dp, ThemeColors.EmeraldAccent, MaterialTheme.shapes.medium)
                .background(ThemeColors.SurfaceDark)
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.headlineSmall,
                color = ThemeColors.EmeraldAccent,
            )
            Text(
                text = "Version ${updateInfo.versionName} (build ${updateInfo.versionCode})",
                color = ThemeColors.TextPrimary,
            )
            Text(
                text = "Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = ThemeColors.TextPrimary.copy(alpha = 0.8f),
            )

            if (updateInfo.changelog.isNotBlank()) {
                Text(
                    text = "What's new",
                    style = MaterialTheme.typography.titleSmall,
                    color = ThemeColors.TextPrimary,
                )
                Text(
                    text = updateInfo.changelog,
                    color = ThemeColors.TextPrimary.copy(alpha = 0.85f),
                )
            }

            if (isDownloading) {
                val progressLabel = if (downloadProgress in 0..100) {
                    "Downloading... $downloadProgress%"
                } else {
                    "Downloading..."
                }
                Text(text = progressLabel, color = ThemeColors.CobaltAccent)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onUpdateNow,
                    enabled = !isDownloading,
                    modifier = Modifier.border(2.dp, ThemeColors.EmeraldAccent, MaterialTheme.shapes.small),
                ) {
                    Text("Update Now", color = ThemeColors.TextPrimary)
                }
                Button(
                    onClick = onLater,
                    enabled = !isDownloading,
                ) {
                    Text("Later")
                }
            }
        }
    }
}
