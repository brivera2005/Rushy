# Rushy

**Rushy** is a standalone, ultra-thin **Android TV** hub for your living-room media. Browse IPTV from your **Xtream** portal and libraries from **Plex** on one Leanback-friendly home screen—then hand off playback to the apps you already use.

**No backend server is required** for normal use. The TV app talks directly to Xtream and Plex on your network, caches catalogs locally, and stores credentials securely on the device.

## What you need

- An **Android TV** box or TV with sideloading enabled (Developer options / unknown apps)
- **[TiviMate](https://play.google.com/store/apps/details?id=ar.tvplayer.tv)** (or compatible) for Xtream live TV and VOD playback
- **[Plex](https://www.plex.tv/)** app on the same device if you want Plex movies and shows
- Your **Xtream portal URL**, username, and password (same credentials you use in TiviMate)
- Optional: **Plex server URL** and **X-Plex-Token** for your Plex library

## Features

- **Xtream IPTV** — Live channels, movies, and series from your provider via `player_api.php`
- **Plex** — Browse your server's on-demand libraries (optional; you can skip Plex in setup)
- **Voice search** — Fuzzy local search over cached titles (no cloud required)
- **Favorites & hidden items** — Persisted in a local JSON cache on the TV
- **Demo mode** — Try the UI instantly with sample rows (no credentials)
- **Encrypted credentials** — Portal and tokens stored with Android EncryptedSharedPreferences

Playback is delegated to TiviMate (`tivimate://…`) and the Plex app (`plex://…`), so Rushy stays lightweight and familiar.

## First-time setup (on the TV)

1. Install the Rushy APK (see [Build APK](#build-apk) below).
2. Launch Rushy and follow the setup wizard:
   - **Xtream** — Portal URL, username, password (required for IPTV)
   - **Plex** — Server URL + token (optional; skip if you only use IPTV)
3. Tap **Done** — the app syncs catalogs on the device.
4. Or choose **Demo mode** to explore the interface without signing in.

An advanced option exposes a legacy backend URL; you can ignore it unless you run the deprecated server (see below).

## Build APK

### Prerequisites

- **JDK 17** or newer
- **Android SDK** with **API level 34**

### First-time Gradle wrapper (if needed)

```bash
cd frontend
gradle wrapper --gradle-version 8.2
```

### Build debug APK

```bash
cd frontend

# Windows
gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

APK output: `frontend/app/build/outputs/apk/debug/app-debug.apk`

### Install on Android TV

1. Enable **Developer options** and allow **Install unknown apps** for your file manager or ADB.
2. Copy `app-debug.apk` to the TV (USB, ADB, or network share).
3. Install and open Rushy from the launcher.
4. Complete setup with your Xtream (and optional Plex) details.

## How it works (developers)

| Piece | Role |
|-------|------|
| Compose TV UI | Home rows, setup wizard, search |
| `XtreamClient` / `PlexClient` | Direct HTTP to provider APIs on device |
| `LocalMediaRepository` | Cache, favorites, hidden flags |
| `LocalSearchEngine` | Voice / text fuzzy search on cached titles |
| `CredentialStore` | Encrypted portal and token storage |

```
Rushy/
├── AGENTS.md          # Contributor / agent notes
├── backend/           # (Optional) legacy FastAPI server
└── frontend/          # Standalone Android TV app
```

## Backend (deprecated, optional)

The `backend/` folder is a **legacy** FastAPI orchestration server. The TV app **does not depend on it**. Use it only if you want server-side sync, Gemini-powered search, or multi-tenant management.

```bash
cd backend
cp .env.example .env   # edit with your values; never commit real secrets
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## License

Add a license file if you plan to open-source distribution terms; otherwise treat this repository as the source for your personal build.

## Contributing

See `AGENTS.md` for architecture notes aimed at developers and coding agents working in this repo.