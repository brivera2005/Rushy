package com.streamvault.app.ui.screens.settings

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.streamvault.app.R
import com.streamvault.app.update.AppUpdateDownloadStatus
import com.streamvault.app.update.AppUpdateInstaller
import com.streamvault.app.update.GitHubReleaseChecker
import com.streamvault.data.preferences.PreferencesRepository
import com.streamvault.domain.model.Result

internal class SettingsAppUpdateActions(
    private val appContext: Application,
    private val preferencesRepository: PreferencesRepository,
    private val gitHubReleaseChecker: GitHubReleaseChecker,
    private val appUpdateInstaller: AppUpdateInstaller,
    private val uiState: MutableStateFlow<SettingsUiState>
) {
    private var updateCheckInFlight = false

    fun shouldAutoCheckForUpdates(lastCheckedAt: Long?): Boolean {
        val now = System.currentTimeMillis()
        val checkIntervalMs = 24L * 60L * 60L * 1000L
        return lastCheckedAt == null || now - lastCheckedAt >= checkIntervalMs
    }

    fun checkForAppUpdates(
        scope: CoroutineScope,
        manual: Boolean,
        isRemoteVersionNewer: (Int?, String) -> Boolean,
        autoDownload: Boolean = false
    ) {
        if (updateCheckInFlight) return
        updateCheckInFlight = true
        scope.launch {
            val checkedAt = System.currentTimeMillis()
            uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    appUpdate = it.appUpdate.copy(errorMessage = null)
                )
            }
            preferencesRepository.setLastAppUpdateCheckTimestamp(checkedAt)
            when (val result = gitHubReleaseChecker.fetchLatestRelease()) {
                is Result.Error -> {
                    uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            userMessage = if (manual) result.message else it.userMessage,
                            appUpdate = it.appUpdate.copy(
                                lastCheckedAt = checkedAt,
                                errorMessage = result.message
                            )
                        )
                    }
                }
                is Result.Success -> {
                    val release = result.data
                    preferencesRepository.setCachedAppUpdateRelease(
                        versionName = release.versionName,
                        versionCode = release.versionCode,
                        releaseUrl = release.releaseUrl,
                        downloadUrl = release.downloadUrl,
                        releaseNotes = release.releaseNotes,
                        publishedAt = release.publishedAt
                    )
                    val updateAvailable = isRemoteVersionNewer(
                        release.versionCode,
                        release.versionName
                    )
                    uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            userMessage = if (manual) {
                                if (updateAvailable) {
                                    appContext.getString(R.string.settings_update_available_message, release.versionName)
                                } else {
                                    appContext.getString(R.string.settings_update_current_message)
                                }
                            } else {
                                it.userMessage
                            },
                            appUpdate = AppUpdateUiModel(
                                latestVersionName = release.versionName,
                                latestVersionCode = release.versionCode,
                                releaseUrl = release.releaseUrl,
                                downloadUrl = release.downloadUrl,
                                releaseNotes = release.releaseNotes,
                                publishedAt = release.publishedAt,
                                isUpdateAvailable = updateAvailable,
                                lastCheckedAt = checkedAt,
                                errorMessage = null
                            ).withDownloadState(it.appUpdate.toDownloadState())
                        )
                    }
                    appUpdateInstaller.refreshState()
                    if (autoDownload && updateAvailable) {
                        val currentDownloadStatus = appUpdateInstaller.downloadState.value.status
                        if (currentDownloadStatus != AppUpdateDownloadStatus.Downloading &&
                            currentDownloadStatus != AppUpdateDownloadStatus.Downloaded
                        ) {
                            downloadLatestUpdate(scope)
                        }
                    }
                }
                Result.Loading -> {
                    uiState.update { it.copy(isCheckingForUpdates = false) }
                }
            }
            updateCheckInFlight = false
        }
    }

    fun downloadLatestUpdate(scope: CoroutineScope) {
        val latestRelease = uiState.value.appUpdate.toReleaseInfoOrNull() ?: run {
            uiState.update {
                it.copy(userMessage = appContext.getString(R.string.settings_update_download_unavailable))
            }
            return
        }

        scope.launch {
            when (val result = appUpdateInstaller.startDownload(latestRelease)) {
                is Result.Error -> uiState.update { it.copy(userMessage = result.message) }
                is Result.Success -> uiState.update {
                    it.copy(userMessage = appContext.getString(R.string.settings_update_download_started))
                }
                Result.Loading -> Unit
            }
        }
    }

    fun installDownloadedUpdate(scope: CoroutineScope) {
        scope.launch {
            when (val result = appUpdateInstaller.installDownloadedUpdate()) {
                is Result.Error -> uiState.update { it.copy(userMessage = result.message) }
                is Result.Success -> uiState.update {
                    it.copy(userMessage = appContext.getString(R.string.settings_update_install_started))
                }
                Result.Loading -> Unit
            }
        }
    }
}
