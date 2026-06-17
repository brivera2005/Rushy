# Public GitHub build: no IPTV creds, Trakt baked from local trakt.properties (secret not in git)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path "trakt.properties")) {
    Write-Error "Missing frontend/trakt.properties. Copy trakt.properties.example and add TRAKT_CLIENT_SECRET."
}

$dc = "app\src\main\kotlin\com\rushy\app\DefaultCredentials.kt"
$backup = "$dc.public-build-bak"
$hadCreds = Test-Path $dc
if ($hadCreds) { Copy-Item $dc $backup -Force }

try {
    .\gradlew.bat assembleRelease -PnoIptvCreds=true
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $apk = "app\build\outputs\apk\release\app-release.apk"
    $dest = Join-Path $PSScriptRoot "..\rushy-1.3.1-public.apk"
    Copy-Item $apk $dest -Force
    Write-Host ("Public APK: " + $dest)
} finally {
    if ($hadCreds -and (Test-Path $backup)) {
        Move-Item $backup $dc -Force
    }
}
