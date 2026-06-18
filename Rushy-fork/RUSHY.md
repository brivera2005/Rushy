# Rushy = StreamVault + Plex

This directory is a **fork of [StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV)** — not ynotv. Rushy pivots from a scratch Android TV hub to a proven IPTV base with Plex backup library support layered on top.

## Why StreamVault (not ynotv)

| | ynotv | StreamVault |
|---|---|---|
| Platform | **Windows desktop** (Tauri + React + mpv) | **Android TV** (Kotlin + Compose TV + Media3) |
| Stars | ~152 | ~404 |
| Xtream / EPG | Excellent (desktop) | Full Xtream + M3U + XMLTV + DVR |
| TV D-pad UX | N/A (mouse/keyboard) | Built TV-first |
| Jellyfin precedent | No | Yes — Plex follows same provider pattern |

**ynotv is the wrong platform for Rushy.** It is a polished Windows IPTV player, not an Android TV fork base.

## License (read before distributing)

StreamVault uses a **custom source-available license** (non-commercial, share-alike, attribution required). Forks must:

- Credit David Nashash / Davidona in About and docs
- Keep links to https://github.com/Davidona/StreamVault-IPTV and https://ko-fi.com/davidona
- State "Based on StreamVault" / "Derived from StreamVault"
- Publish source under the same license if you distribute builds

Personal / non-commercial use is fine. Commercial distribution needs written permission from the copyright holder.

## Rushy-specific goals

1. **Primary:** Xtream live TV + VOD via StreamVault (in-app ExoPlayer, EPG, DVR)
2. **Secondary / backup:** Plex server pull for movies & shows when Xtream VOD is down or thin
3. **Unified home:** Live TV rows from Xtream; Movies/Shows/Continue Watching from Plex
4. **Salvaged from legacy Rushy:** `PlexClient`, `PlexWatchlistClient`, `CredentialStore` pattern, Trakt, OTA updates

## Plex integration plan

StreamVault already implements **Jellyfin** as a `ProviderType` with `JellyfinProvider` in `data/`. Plex should mirror that:

```
ProviderType.PLEX  →  PlexProvider (REST, token auth)
                   →  sync movies/series into Room (reuse MovieEntity/SeriesEntity)
                   →  home dashboard shelves: "Plex Continue", "Plex Watchlist"
                   →  fallback: if Xtream sync fails, show Plex VOD shelves
```

Playback options:

- **Phase 1:** Hand off to Plex app intent (`plex://…`) — same as legacy Rushy
- **Phase 2:** In-app Media3 direct stream URLs from Plex server

Credentials: `PlexCredentialStore` (encrypted prefs) — stub in `data/remote/plex/`.

## Build

```bash
cd Rushy-fork
gradlew.bat assembleDebug   # Windows
./gradlew assembleDebug     # Linux/macOS
```

## Migration from `frontend/`

Legacy Rushy (`frontend/`) stays intact until you approve removal. Salvage map:

| Rushy file | Action in fork |
|---|---|
| `PlexClient.kt` | Ported → `data/.../plex/PlexClient.kt` |
| `PlexWatchlistClient.kt` | Port in week 2 |
| `CredentialStore.kt` | Pattern → `PlexCredentialStore.kt` + StreamVault `PreferencesRepository` |
| `DefaultCredentials.kt` | `local.properties` / build config (dev only) |
| `Trakt*.kt` | Optional week 3 — trending rows on home |
| `ApkUpdateManager.kt` | StreamVault has its own; merge GitHub release config |
| `XtreamClient.kt` | **Drop** — StreamVault data layer replaces it |

## Timeline

- **Week 1:** Fork builds; Plex settings stub; credential store; validate Plex token
- **Week 2:** `PlexProvider` sync → Room; home shelves; Xtream-fail fallback
- **Week 3:** Watchlist / continue watching; Trakt; polish; optional replace `frontend/`

## Upstream

```bash
git remote add upstream https://github.com/Davidona/StreamVault-IPTV.git
git fetch upstream
git merge upstream/master
```
