package com.rushy.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class DownloadResult {
    data class Success(val apkFile: File) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

class ApkUpdateManager(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private val releaseClient = GitHubReleaseClient(httpClient)

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        releaseClient.checkForUpdate(BuildConfig.VERSION_CODE)
    }

    suspend fun downloadApk(
        updateInfo: UpdateInfo,
        onProgress: ((Int) -> Unit)? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "Rushy-Android-TV")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error("Download failed (${response.code})")
                }

                val body = response.body
                    ?: return@withContext DownloadResult.Error("Empty download response")

                val totalBytes = body.contentLength().coerceAtLeast(0L)
                val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, updateInfo.apkFileName)

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                onProgress?.invoke(percent)
                            }
                            read = input.read(buffer)
                        }
                    }
                }

                DownloadResult.Success(apkFile)
            }
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Download failed.")
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
            activity.startActivity(intent)
            Toast.makeText(
                context,
                "Allow installs from this app, then try again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Toast.makeText(context, "APK file missing.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            Toast.makeText(
                context,
                "Install permission required. Open Settings → Updates.",
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Could not start installer.",
                Toast.LENGTH_LONG,
            ).show()
            false
        }
    }
}
