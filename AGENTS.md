# Rushy — Agent Guide

Standalone Android TV client. **No backend required** for normal operation.

## Architecture

- **Ultra-thin client**: Compose TV UI + local JSON cache + direct HTTP to Plex & Xtream on device
- **Xtream**: `player_api.php` via `XtreamClient.kt` → playback via TiviMate intent
- **Plex**: REST API via `PlexClient.kt` → playback via Plex app intent
- **Local**: `LocalMediaRepository.kt` (cache, favorites, hidden), `LocalSearchEngine.kt` (fuzzy search)
- **Credentials**: `CredentialStore.kt` (EncryptedSharedPreferences)

## Build

```bash
cd frontend
gradlew.bat assembleDebug   # Windows
./gradlew assembleDebug     # Linux/macOS
```

APK: `frontend/app/build/outputs/apk/debug/app-debug.apk`

## Backend (deprecated)

The `backend/` folder is optional legacy orchestration. The TV app does not depend on it.
