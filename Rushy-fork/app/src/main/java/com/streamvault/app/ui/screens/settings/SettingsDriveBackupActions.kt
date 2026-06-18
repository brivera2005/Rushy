package com.streamvault.app.ui.screens.settings

import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import android.util.Log
import com.streamvault.domain.manager.DriveAuthState
import com.streamvault.domain.manager.DriveBackupSyncManager
import com.streamvault.domain.manager.DriveSyncStatus
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.usecase.ImportBackup
import com.streamvault.domain.usecase.InspectBackupCommand
import com.streamvault.domain.usecase.InspectBackupResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drive backup orchestration helper. Mirrors the [SettingsBackupActions] pattern:
 * internal class, [MutableStateFlow] uiState injected, methods take the caller
 * [CoroutineScope] and run their body in `scope.launch { uiState.update { ... } }`.
 *
 * Pull deliberately goes through the [ImportBackup.inspect] usecase rather than
 * calling [com.streamvault.domain.manager.BackupManager.inspectBackup] directly,
 * so the existing David SAF preview dialog is reused unchanged (decision D2).
 */
internal class SettingsDriveBackupActions(
    private val driveManager: DriveBackupSyncManager,
    private val importBackup: ImportBackup,
    private val providerRepository: ProviderRepository,
    private val uiState: MutableStateFlow<SettingsUiState>
) {

    /**
     * Collects [DriveBackupSyncManager.authState] into [SettingsUiState.driveAuthState].
     * Call once from the owning ViewModel `init` block.
     */
    fun observeAuthState(scope: CoroutineScope) {
        scope.launch {
            driveManager.authState.collect { state ->
                uiState.update { it.copy(driveAuthState = state) }
            }
        }
        scope.launch {
            driveManager.syncStatus.collect { status ->
                uiState.update {
                    it.copy(
                        driveSyncStatus = status,
                        driveLastPushAt = status.lastPushAtMs ?: it.driveLastPushAt,
                        driveLastPullAt = status.lastPullAtMs ?: it.driveLastPullAt
                    )
                }
            }
        }
    }

    /**
     * Requests a Sign-In intent from the manager then forwards it to the
     * Activity-bound [launcher]. The result is sent back via [completeSignIn].
     */
    fun beginSignIn(scope: CoroutineScope, launcher: ActivityResultLauncher<Intent>) {
        scope.launch {
            uiState.update { it.copy(driveIsBusy = true) }
            val result = driveManager.beginSignIn()
            uiState.update { state ->
                when (result) {
                    is Result.Success -> {
                        val intent = result.data.intent as? Intent
                        if (intent != null) {
                            runCatching { launcher.launch(intent) }
                                .onFailure {
                                    return@update state.copy(
                                        driveIsBusy = false,
                                        userMessage = "Drive sign-in failed: ${it.message ?: "unable to launch"}"
                                    )
                                }
                            state.copy(driveIsBusy = false, drivePendingSignIn = result.data)
                        } else {
                            state.copy(
                                driveIsBusy = false,
                                userMessage = "Drive sign-in failed: missing intent"
                            )
                        }
                    }
                    is Result.Error -> state.copy(
                        driveIsBusy = false,
                        userMessage = "Drive sign-in failed: ${result.message}"
                    )
                    is Result.Loading -> state.copy(driveIsBusy = false)
                }
            }
        }
    }

    fun completeSignIn(scope: CoroutineScope, intentData: Intent?) {
        scope.launch {
            uiState.update { it.copy(driveIsBusy = true) }
            val result = driveManager.completeSignIn(intentData)
            uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(
                        driveIsBusy = false,
                        drivePendingSignIn = null,
                        userMessage = result.data.email?.let { "Signed in as $it" }
                            ?: "Signed in to Google Drive"
                    )
                    is Result.Error -> state.copy(
                        driveIsBusy = false,
                        drivePendingSignIn = null,
                        userMessage = "Drive sign-in failed: ${result.message}"
                    )
                    is Result.Loading -> state.copy(driveIsBusy = false)
                }
            }
        }
    }

    fun signOut(scope: CoroutineScope) {
        scope.launch {
            uiState.update { it.copy(driveIsBusy = true) }
            val result = driveManager.signOut()
            uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(
                        driveIsBusy = false,
                        driveSyncStatus = DriveSyncStatus(),
                        driveLastPushAt = null,
                        driveLastPullAt = null,
                        userMessage = "Signed out of Google Drive"
                    )
                    is Result.Error -> state.copy(
                        driveIsBusy = false,
                        userMessage = "Drive sign-out failed: ${result.message}"
                    )
                    is Result.Loading -> state.copy(driveIsBusy = false)
                }
            }
        }
    }

    fun pushBackup(scope: CoroutineScope) {
        scope.launch {
            if (uiState.value.driveAuthState !is DriveAuthState.SignedIn) {
                uiState.update { it.copy(userMessage = "Sign in to Google Drive first") }
                return@launch
            }
            uiState.update { it.copy(driveIsBusy = true) }
            val backupResult = driveManager.pushBackup()
            if (backupResult is Result.Error) {
                uiState.update {
                    it.copy(
                        driveIsBusy = false,
                        userMessage = "Drive push failed: ${backupResult.message}"
                    )
                }
                return@launch
            }
            // Chain credentials push (M3). The repository handles decryption
            // internally so the cleartext never crosses the data layer except
            // via the typed ProviderCredentials payload returned here.
            val credentials = providerRepository.getAllProviderCredentials()
            val credsResult = driveManager.pushCredentials(credentials)
            uiState.update { state ->
                when (credsResult) {
                    is Result.Success -> state.copy(
                        driveIsBusy = false,
                        userMessage = "Backup uploaded to Google Drive"
                    )
                    is Result.Error -> state.copy(
                        driveIsBusy = false,
                        userMessage = "Backup uploaded but credentials failed: ${credsResult.message}"
                    )
                    is Result.Loading -> state.copy(driveIsBusy = false)
                }
            }
        }
    }

    fun pullBackup(scope: CoroutineScope) {
        scope.launch {
            if (uiState.value.driveAuthState !is DriveAuthState.SignedIn) {
                uiState.update { it.copy(userMessage = "Sign in to Google Drive first") }
                return@launch
            }
            uiState.update { it.copy(driveIsBusy = true) }
            val pullResult = driveManager.pullBackup()
            if (pullResult is Result.Error) {
                uiState.update {
                    it.copy(
                        driveIsBusy = false,
                        userMessage = "Drive pull failed: ${pullResult.message}"
                    )
                }
                return@launch
            }
            val artifact = (pullResult as Result.Success).data
            // Best-effort companion fetch (M3). Failures here are non-fatal —
            // the user can still complete the import without credentials.
            val credentialsResult = driveManager.pullCredentials()
            val pendingCredentials = (credentialsResult as? Result.Success)?.data
            if (credentialsResult is Result.Error) {
                Log.w("DriveSync", "pullCredentials failed (non-fatal): ${credentialsResult.message}")
            }
            val inspectResult = importBackup.inspect(InspectBackupCommand(artifact.localUriString))
            uiState.update { state ->
                when (inspectResult) {
                    is InspectBackupResult.Success -> state.copy(
                        driveIsBusy = false,
                        pendingBackupUri = inspectResult.uriString,
                        backupPreview = inspectResult.preview,
                        backupImportPlan = inspectResult.defaultPlan,
                        pendingDriveCredentials = pendingCredentials,
                    )
                    is InspectBackupResult.Error -> state.copy(
                        driveIsBusy = false,
                        userMessage = "Drive import failed: ${inspectResult.message}"
                    )
                }
            }
        }
    }

    /**
     * Applies the credentials downloaded by [pullBackup] to the providers that
     * were just restored by the SAF import dialog. Match key is
     * `(serverUrl, username)` so the row ids reshuffled by import don't matter.
     * Re-encrypts via [CredentialCrypto] before writing to the local DB.
     *
     * Call this from the ViewModel **after** the import confirm succeeds.
     */
    fun applyPendingCredentials(scope: CoroutineScope) {
        scope.launch {
            val pending = uiState.value.pendingDriveCredentials.orEmpty()
            if (pending.isEmpty()) return@launch
            var applied = 0
            pending.forEach { cred ->
                if (providerRepository.updateProviderPassword(
                        serverUrl = cred.serverUrl,
                        username = cred.username,
                        cleartextPassword = cred.password,
                    )
                ) {
                    applied++
                }
            }
            Log.d("DriveSync", "applyPendingCredentials: $applied / ${pending.size} matched")
            uiState.update { it.copy(pendingDriveCredentials = null) }
        }
    }
}
