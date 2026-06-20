package com.streamvault.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.streamvault.app.BuildConfig

data class InstalledAppVersion(
    val versionCode: Int,
    val versionName: String,
) {
    companion object {
        fun read(context: Context): InstalledAppVersion {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val versionName = packageInfo.versionName?.trim().orEmpty().ifBlank { BuildConfig.VERSION_NAME }

            return InstalledAppVersion(
                versionCode = versionCode,
                versionName = versionName,
            )
        }
    }
}
