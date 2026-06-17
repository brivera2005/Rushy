package com.rushy.app

/**
 * GitHub release source for in-app updates.
 *
 * Change [GITHUB_OWNER] and [GITHUB_REPO] to match your fork before building.
 * Publish releases with an APK asset (e.g. `rushy-release.apk`) and either:
 * - a `version.json` asset: `{"versionCode":2,"versionName":"1.0.1"}`, or
 * - a release body line: `versionCode: 2`
 */
object UpdateConfig {
    const val GITHUB_OWNER = "brivera2005"
    const val GITHUB_REPO = "Rushy"

    const val APK_ASSET_SUFFIX = ".apk"
    const val VERSION_JSON_ASSET = "version.json"

    val releasesApiUrl: String
        get() = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
}
