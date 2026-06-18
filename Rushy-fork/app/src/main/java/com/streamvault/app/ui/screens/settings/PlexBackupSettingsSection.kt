package com.streamvault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.app.R
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.app.ui.theme.OnSurfaceDim
import com.streamvault.app.ui.theme.Primary
import com.streamvault.data.remote.plex.PlexClient
import com.streamvault.data.remote.plex.PlexCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LazyListScope.plexBackupSettingsSection(
    plexCredentials: PlexCredentialStore,
    plexClient: PlexClient,
) {
    item {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Spacer(modifier = Modifier.height(12.dp))
        PlexBackupSettingsCard(
            plexCredentials = plexCredentials,
            plexClient = plexClient,
        )
    }
}

@Composable
private fun PlexBackupSettingsCard(
    plexCredentials: PlexCredentialStore,
    plexClient: PlexClient,
) {
    var serverUrl by remember { mutableStateOf(plexCredentials.serverUrl) }
    var token by remember { mutableStateOf(plexCredentials.token) }
    var backupEnabled by remember { mutableStateOf(plexCredentials.backupEnabled) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.rushy_plex_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = Primary,
        )
        Text(
            text = stringResource(R.string.rushy_plex_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim,
        )

        PlexTextField(
            label = stringResource(R.string.rushy_plex_server_url),
            value = serverUrl,
            onValueChange = { serverUrl = it },
        )
        PlexTextField(
            label = stringResource(R.string.rushy_plex_token),
            value = token,
            onValueChange = { token = it },
            maskInput = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rushy_plex_backup_enabled),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = backupEnabled,
                onCheckedChange = {
                    backupEnabled = it
                    plexCredentials.backupEnabled = it
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvClickableSurface(
                onClick = {
                    plexCredentials.save(serverUrl, token)
                    plexCredentials.backupEnabled = backupEnabled
                    statusMessage = context.getString(R.string.rushy_plex_saved)
                },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.2f),
                    focusedContainerColor = Primary.copy(alpha = 0.35f),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = stringResource(R.string.rushy_plex_save),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            TvClickableSurface(
                onClick = {
                    if (serverUrl.isBlank() || token.isBlank()) {
                        statusMessage = context.getString(R.string.rushy_plex_missing_fields)
                        return@TvClickableSurface
                    }
                    isTesting = true
                    statusMessage = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                plexClient.forCredentials(serverUrl, token).validateCredentials()
                            }.getOrDefault(false)
                        }
                        isTesting = false
                        statusMessage = if (ok) {
                            context.getString(R.string.rushy_plex_test_ok)
                        } else {
                            context.getString(R.string.rushy_plex_test_failed)
                        }
                    }
                },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = if (isTesting) {
                        stringResource(R.string.rushy_plex_testing)
                    } else {
                        stringResource(R.string.rushy_plex_test)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun PlexTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maskInput: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceDim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Primary),
            visualTransformation = if (maskInput) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
        )
    }
}
