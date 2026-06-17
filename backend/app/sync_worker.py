import requests

from app.config import settings
from app.database import MediaStream, SessionLocal, UserProfile


def execute_daily_plex_prune() -> None:
    """Cross-reference Plex library titles with cached IPTV catalogs and prune duplicates."""
    db = SessionLocal()
    try:
        profiles = db.query(UserProfile).all()

        for user in profiles:
            if not user.plex_server_url or not user.xtream_portal:
                continue

            plex_headers = {"X-Plex-Token": user.plex_token, "Accept": "application/json"}
            plex_res = requests.get(
                f"{user.plex_server_url}/library/sections/1/all",
                headers=plex_headers,
                timeout=settings.http_timeout,
            )
            if plex_res.status_code != 200:
                continue

            plex_items = plex_res.json().get("MediaContainer", {}).get("Metadata", [])

            for plex_item in plex_items:
                title = plex_item.get("title")
                if not title:
                    continue

                clean_title = title.strip().lower()

                match = db.query(MediaStream).filter(
                    MediaStream.tenant_id == user.id,
                    MediaStream.stream_type == "movie",
                    MediaStream.title.match(clean_title),
                ).first()

                if match:
                    rating_key = plex_item.get("ratingKey")
                    if rating_key:
                        requests.delete(
                            f"{user.plex_server_url}/library/metadata/{rating_key}",
                            headers=plex_headers,
                            timeout=settings.http_timeout,
                        )
    finally:
        db.close()
