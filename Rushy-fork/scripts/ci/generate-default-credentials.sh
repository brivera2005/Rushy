#!/usr/bin/env bash
# Generates gitignored DefaultCredentials.kt from CI secrets (never commit real values).
set -euo pipefail

OUT="${1:-app/src/main/java/com/streamvault/app/DefaultCredentials.kt}"

escape_kotlin() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

PORTAL="$(escape_kotlin "${RUSHY_XTREAM_PORTAL:-}")"
USER="$(escape_kotlin "${RUSHY_XTREAM_USER:-}")"
PASS="$(escape_kotlin "${RUSHY_XTREAM_PASS:-}")"
PLEX_URL="$(escape_kotlin "${RUSHY_PLEX_URL:-}")"
PLEX_TOKEN="$(escape_kotlin "${RUSHY_PLEX_TOKEN:-}")"
RADARR_URL="$(escape_kotlin "${RUSHY_RADARR_URL:-}")"
RADARR_KEY="$(escape_kotlin "${RUSHY_RADARR_API_KEY:-}")"
SONARR_URL="$(escape_kotlin "${RUSHY_SONARR_URL:-}")"
SONARR_KEY="$(escape_kotlin "${RUSHY_SONARR_API_KEY:-}")"
AUTO_APPLY="${RUSHY_AUTO_APPLY:-true}"

mkdir -p "$(dirname "$OUT")"
cat > "$OUT" <<EOF
package com.streamvault.app

/** Generated at build time from GitHub Actions secrets — do not commit. */
object DefaultCredentials {
    const val PORTAL_URL = "$PORTAL"
    const val USERNAME = "$USER"
    const val PASSWORD = "$PASS"

    const val PLEX_SERVER_URL = "$PLEX_URL"
    const val PLEX_TOKEN = "$PLEX_TOKEN"

    const val RADARR_URL = "$RADARR_URL"
    const val RADARR_API_KEY = "$RADARR_KEY"
    const val SONARR_URL = "$SONARR_URL"
    const val SONARR_API_KEY = "$SONARR_KEY"

    const val AUTO_APPLY = $AUTO_APPLY
}
EOF

echo "Wrote DefaultCredentials.kt (AUTO_APPLY=$AUTO_APPLY, portal=${PORTAL:-empty})"
