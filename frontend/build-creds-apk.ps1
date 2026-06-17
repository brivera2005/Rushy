# Personal build: IPTV creds (DefaultCredentials.kt) + Trakt from trakt.properties
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path "trakt.properties")) {
    Write-Error "Missing frontend/trakt.properties. Copy trakt.properties.example and add TRAKT_CLIENT_SECRET."
}
if (-not (Test-Path "app\src\main\kotlin\com\rushy\app\DefaultCredentials.kt")) {
    Write-Error "Missing DefaultCredentials.kt. Copy DefaultCredentials.kt.example and fill IPTV creds."
}

.\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = "app\build\outputs\apk\release\app-release.apk"
$dest = "E:\rushy-1.3.1.apk"
Copy-Item $apk $dest -Force
Write-Host "Creds APK: $dest"
