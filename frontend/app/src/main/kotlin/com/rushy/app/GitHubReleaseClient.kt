package com.rushy.app

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
)

data class GitHubReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Long = 0,
)

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    val name: String?,
    val body: String?,
    val draft: Boolean = false,
    @SerializedName("prerelease")
    val preRelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class GitHubReleaseClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
) {
    fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult {
        if (UpdateConfig.GITHUB_OWNER == "YOUR_GITHUB_USERNAME") {
            return UpdateCheckResult.Error(
                "GitHub repo not configured. Set UpdateConfig.GITHUB_OWNER and GITHUB_REPO.",
            )
        }

        return try {
            val release = fetchLatestRelease()
                ?: return UpdateCheckResult.Error("No published GitHub release found.")

            val apkAsset = release.assets.firstOrNull { asset ->
                asset.name.endsWith(UpdateConfig.APK_ASSET_SUFFIX, ignoreCase = true)
            } ?: return UpdateCheckResult.Error("Release has no APK asset.")

            val versionInfo = resolveVersionInfo(release)
                ?: return UpdateCheckResult.Error("Could not determine release versionCode.")

            if (versionInfo.versionCode <= currentVersionCode) {
                return UpdateCheckResult.UpToDate
            }

            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    versionCode = versionInfo.versionCode,
                    versionName = versionInfo.versionName,
                    changelog = release.body?.trim().orEmpty(),
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkFileName = apkAsset.name,
                ),
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Update check failed.")
        }
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val request = Request.Builder()
            .url(UpdateConfig.releasesApiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Rushy-Android-TV")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub API returned ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, GitHubRelease::class.java)
        }
    }

    private fun resolveVersionInfo(release: GitHubRelease): AppVersionInfo? {
        val jsonAsset = release.assets.firstOrNull { asset ->
            asset.name.equals(UpdateConfig.VERSION_JSON_ASSET, ignoreCase = true)
        }
        if (jsonAsset != null) {
            parseVersionJson(fetchAssetText(jsonAsset.browserDownloadUrl))?.let { return it }
        }

        parseVersionCodeFromBody(release.body)?.let { versionCode ->
            val versionName = normalizeTagToVersionName(release.tagName)
            return AppVersionInfo(versionCode = versionCode, versionName = versionName)
        }

        parseVersionCodeFromTag(release.tagName)?.let { versionCode ->
            return AppVersionInfo(
                versionCode = versionCode,
                versionName = normalizeTagToVersionName(release.tagName),
            )
        }

        return null
    }

    private fun fetchAssetText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Rushy-Android-TV")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            return response.body?.string().orEmpty()
        }
    }

    private fun parseVersionJson(json: String): AppVersionInfo? {
        if (json.isBlank()) return null
        return try {
            val parsed = gson.fromJson(json, AppVersionInfo::class.java)
            if (parsed.versionCode > 0) parsed else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersionCodeFromBody(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        val match = VERSION_CODE_BODY_REGEX.find(body) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun parseVersionCodeFromTag(tagName: String): Int? {
        val match = VERSION_CODE_TAG_REGEX.find(tagName) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun normalizeTagToVersionName(tagName: String): String =
        tagName.removePrefix("v").removePrefix("V")

    companion object {
        private val VERSION_CODE_BODY_REGEX =
            Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val VERSION_CODE_TAG_REGEX =
            Regex("""(?:^|[vV-])(\d+)$""")
    }
}
