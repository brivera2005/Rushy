package com.streamvault.app.ui.screens.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.streamvault.data.remote.arr.ArrCredentialStore
import com.streamvault.data.remote.arr.RadarrClient
import com.streamvault.data.remote.arr.SonarrClient
import kotlinx.coroutines.launch

internal fun LazyListScope.arrSettingsSection(
    arrCredentials: ArrCredentialStore,
    radarrClient: RadarrClient,
    sonarrClient: SonarrClient,
) {
    item {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Spacer(modifier = Modifier.height(12.dp))
        ArrSettingsCard(
            arrCredentials = arrCredentials,
            radarrClient = radarrClient,
            sonarrClient = sonarrClient,
        )
    }
}

@Composable
private fun ArrSettingsCard(
    arrCredentials: ArrCredentialStore,
    radarrClient: RadarrClient,
    sonarrClient: SonarrClient,
) {
    var radarrUrl by remember { mutableStateOf(arrCredentials.radarrUrl) }
    var radarrApiKey by remember { mutableStateOf(arrCredentials.radarrApiKey) }
    var sonarrUrl by remember { mutableStateOf(arrCredentials.sonarrUrl) }
    var sonarrApiKey by remember { mutableStateOf(arrCredentials.sonarrApiKey) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingRadarr by remember { mutableStateOf(false) }
    var isTestingSonarr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val radarrConnectedLabel = stringResource(R.string.settings_arr_radarr_connected)
    val sonarrConnectedLabel = stringResource(R.string.settings_arr_sonarr_connected)
    val savedLabel = stringResource(R.string.settings_arr_saved)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_arr_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = Primary,
        )
        Text(
            text = stringResource(R.string.settings_arr_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim,
        )

        ArrCredentialField(
            label = stringResource(R.string.settings_radarr_url_hint),
            value = radarrUrl,
            onValueChange = { radarrUrl = it },
        )
        ArrCredentialField(
            label = stringResource(R.string.settings_radarr_api_key_hint),
            value = radarrApiKey,
            onValueChange = { radarrApiKey = it },
        )
        ArrCredentialField(
            label = stringResource(R.string.settings_sonarr_url_hint),
            value = sonarrUrl,
            onValueChange = { sonarrUrl = it },
        )
        ArrCredentialField(
            label = stringResource(R.string.settings_sonarr_api_key_hint),
            value = sonarrApiKey,
            onValueChange = { sonarrApiKey = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionChip(
                label = stringResource(R.string.settings_arr_test_radarr),
                enabled = !isTestingRadarr,
                onClick = {
                    scope.launch {
                        isTestingRadarr = true
                        arrCredentials.saveRadarr(radarrUrl, radarrApiKey)
                        val result = radarrClient.testConnection()
                        statusMessage = if (result.isSuccess) {
                            radarrConnectedLabel
                        } else {
                            context.getString(
                                R.string.settings_arr_connection_failed,
                                result.errorMessageOrNull().orEmpty(),
                            )
                        }
                        isTestingRadarr = false
                    }
                },
            )
            SettingsActionChip(
                label = stringResource(R.string.settings_arr_test_sonarr),
                enabled = !isTestingSonarr,
                onClick = {
                    scope.launch {
                        isTestingSonarr = true
                        arrCredentials.saveSonarr(sonarrUrl, sonarrApiKey)
                        val result = sonarrClient.testConnection()
                        statusMessage = if (result.isSuccess) {
                            sonarrConnectedLabel
                        } else {
                            context.getString(
                                R.string.settings_arr_connection_failed,
                                result.errorMessageOrNull().orEmpty(),
                            )
                        }
                        isTestingSonarr = false
                    }
                },
            )
        }

        SettingsActionChip(
            label = stringResource(R.string.settings_arr_save),
            onClick = {
                arrCredentials.saveRadarr(radarrUrl, radarrApiKey)
                arrCredentials.saveSonarr(sonarrUrl, sonarrApiKey)
                statusMessage = savedLabel
            },
        )

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Primary,
            )
        }
    }
}

@Composable
private fun ArrCredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = OnSurfaceDim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(Primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                TvClickableSurface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    inner()
                }
            },
        )
    }
}

@Composable
private fun SettingsActionChip(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TvClickableSurface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Primary.copy(alpha = 0.18f),
            focusedContainerColor = Primary.copy(alpha = 0.32f),
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Primary else OnSurfaceDim,
        )
    }
}
