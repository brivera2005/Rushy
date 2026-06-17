package com.rushy.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
fun SetupWizardView(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials = remember { CredentialStore.getInstance(context) }

    var step by remember { mutableStateOf(1) }
    var xtreamPortal by remember { mutableStateOf(credentials.xtreamPortal) }
    var xtreamUser by remember { mutableStateOf(credentials.xtreamUsername) }
    var xtreamPass by remember { mutableStateOf(credentials.xtreamPassword) }
    var plexServerUrl by remember { mutableStateOf(credentials.plexServerUrl) }
    var plexToken by remember { mutableStateOf(credentials.plexToken) }
    var backendUrl by remember { mutableStateOf(credentials.backendUrl) }
    var showAdvanced by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RushyLogo(
                size = RushyLogoSize.Large,
                showTagline = true,
            )
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineSmall,
                color = ThemeColors.TextPrimary.copy(alpha = 0.85f),
            )

            when (step) {
                1 -> {
                    Text(
                        text = "Step 1: Xtream portal credentials (required for IPTV)",
                        color = ThemeColors.TextPrimary,
                    )
                    CredentialField(
                        label = "Portal URL",
                        value = xtreamPortal,
                        placeholder = "http://provider.example:8080",
                        onValueChange = { xtreamPortal = it },
                    )
                    CredentialField(
                        label = "Username",
                        value = xtreamUser,
                        placeholder = "TiviMate username",
                        onValueChange = { xtreamUser = it },
                    )
                    CredentialField(
                        label = "Password",
                        value = xtreamPass,
                        placeholder = "TiviMate password",
                        onValueChange = { xtreamPass = it },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (xtreamPortal.isBlank() || xtreamUser.isBlank() || xtreamPass.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Enter portal, username, and password.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@Button
                                }
                                credentials.saveXtream(xtreamPortal, xtreamUser, xtreamPass)
                                step = 2
                            },
                        ) {
                            Text("Next")
                        }
                        Button(
                            onClick = {
                                credentials.enableDemoMode()
                                onSetupComplete()
                            },
                        ) {
                            Text("Demo Mode")
                        }
                    }
                }

                2 -> {
                    Text(
                        text = "Step 2: Plex server (optional)",
                        color = ThemeColors.TextPrimary,
                    )
                    CredentialField(
                        label = "Plex server URL",
                        value = plexServerUrl,
                        placeholder = "http://192.168.1.10:32400",
                        onValueChange = { plexServerUrl = it },
                    )
                    CredentialField(
                        label = "X-Plex-Token",
                        value = plexToken,
                        placeholder = "Your Plex token",
                        onValueChange = { plexToken = it },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { step = 1 }) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                if (plexServerUrl.isNotBlank() && plexToken.isNotBlank()) {
                                    credentials.savePlex(plexServerUrl, plexToken)
                                }
                                step = 3
                            },
                        ) {
                            Text("Next")
                        }
                        Button(
                            onClick = {
                                credentials.plexServerUrl = ""
                                credentials.plexToken = ""
                                step = 3
                            },
                        ) {
                            Text("Skip Plex")
                        }
                    }
                }

                3 -> {
                    Text(
                        text = "Step 3: Ready",
                        color = ThemeColors.TextPrimary,
                    )
                    Text(
                        text = "Rushy will sync catalogs directly on this device — no backend required.",
                        color = ThemeColors.TextPrimary,
                    )

                    Button(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "Hide Advanced" else "Advanced")
                    }

                    if (showAdvanced) {
                        Text(
                            text = "Optional legacy backend URL (deprecated)",
                            color = ThemeColors.CobaltAccent,
                        )
                        CredentialField(
                            label = "Backend URL",
                            value = backendUrl,
                            placeholder = "http://192.168.1.100:8000",
                            onValueChange = { backendUrl = it },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { step = 2 }) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                if (isSubmitting) return@Button
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        if (backendUrl.isNotBlank()) {
                                            credentials.backendUrl = backendUrl
                                        }
                                        credentials.completeSetup()
                                        onSetupComplete()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Setup failed.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                        ) {
                            Text(if (isSubmitting) "Saving..." else "Finish Setup")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = ThemeColors.TextPrimary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ThemeColors.TextPrimary),
            cursorBrush = SolidColor(LocalRushyTheme.current.currentAccentColor),
            modifier = Modifier
                .fillMaxWidth()
                .background(ThemeColors.SurfaceDark)
                .padding(12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, color = ThemeColors.CobaltAccent)
                }
                inner()
            },
        )
    }
}
