package com.streamvault.app.ui.screens.settings

import com.streamvault.app.R
import com.streamvault.app.update.AppUpdateDownloadStatus
import java.text.DateFormat

internal fun formatLatestReleaseLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val versionName = update.latestVersionName ?: return context.getString(R.string.settings_update_not_checked)
    val versionCodeSuffix = update.latestVersionCode?.let { " ($it)" }.orEmpty()
    return "$versionName$versionCodeSuffix"
}

internal fun formatUpdateStatusLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when {
        update.errorMessage != null -> context.getString(R.string.settings_update_status_check_failed)
        update.downloadStatus == AppUpdateDownloadStatus.Downloading -> context.getString(R.string.settings_update_status_downloading)
        update.downloadStatus == AppUpdateDownloadStatus.Downloaded && downloadedReleaseMatchesLatest -> context.getString(R.string.settings_update_status_ready_to_install)
        update.latestVersionName == null -> context.getString(R.string.settings_update_not_checked)
        update.isUpdateAvailable -> context.getString(R.string.settings_update_status_available)
        else -> context.getString(R.string.settings_update_status_current)
    }
}

internal fun formatUpdateCheckTimeLabel(timestamp: Long?, context: android.content.Context): String {
    if (timestamp == null || timestamp <= 0L) {
        return context.getString(R.string.settings_update_not_checked)
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(timestamp))
}

internal fun shouldShowUpdateDownloadAction(update: AppUpdateUiModel): Boolean {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when (update.downloadStatus) {
        AppUpdateDownloadStatus.Downloading,
        AppUpdateDownloadStatus.Downloaded -> downloadedReleaseMatchesLatest || (update.isUpdateAvailable && !update.downloadUrl.isNullOrBlank())
        else -> update.isUpdateAvailable && !update.downloadUrl.isNullOrBlank()
    }
}

internal fun formatUpdateDownloadLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when (update.downloadStatus) {
        AppUpdateDownloadStatus.Downloading -> context.getString(R.string.settings_update_download_in_progress)
        AppUpdateDownloadStatus.Downloaded -> {
            if (downloadedReleaseMatchesLatest) {
                context.getString(R.string.settings_update_install_action)
            } else {
                context.getString(R.string.settings_update_download_action)
            }
        }
        else -> context.getString(R.string.settings_update_download_action)
    }
}