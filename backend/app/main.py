from typing import Any, Optional

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import APIRouter, Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from rapidfuzz import fuzz, process
from sqlalchemy.orm import Session

from app.config import settings
from app.database import MediaStream, SessionLocal, UserProfile, init_db
from app.gemini_service import GeminiMediaService
from app.sync_worker import execute_daily_plex_prune

app = FastAPI(title=settings.app_name, debug=settings.debug)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

scheduler = BackgroundScheduler()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def model_to_dict(instance: Any) -> dict[str, Any]:
    """Serialize SQLAlchemy models without internal state keys."""
    return {
        column.name: getattr(instance, column.name)
        for column in instance.__table__.columns
    }


def validate_user_access(user_id: str) -> None:
    if settings.require_user_header and not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing x-rushy-user-id header.",
        )
    if not settings.is_user_allowed(user_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User profile not authorized for this deployment.",
        )


class ProfileSetupRequest(BaseModel):
    user_id: str
    username: Optional[str] = None
    xtream_portal: Optional[str] = None
    xtream_username: Optional[str] = None
    xtream_password: Optional[str] = None
    plex_token: Optional[str] = None
    plex_server_url: Optional[str] = None


class ToggleVisibilityRequest(BaseModel):
    stream_id: str
    action: str = Field(description='Either "hide" or "favorite"')
    state: bool = True


@app.on_event("startup")
def on_startup() -> None:
    init_db()
    scheduler.add_job(
        execute_daily_plex_prune,
        trigger="cron",
        hour=settings.plex_prune_cron_hour,
        minute=settings.plex_prune_cron_minute,
        id="daily_plex_prune",
        replace_existing=True,
    )
    scheduler.start()


@app.on_event("shutdown")
def on_shutdown() -> None:
    scheduler.shutdown(wait=False)


@app.get("/health")
def health_check() -> dict[str, str]:
    return {"status": "ok", "service": settings.app_name}


@app.post("/api/profiles/setup")
def setup_profile(profile_data: ProfileSetupRequest, db: Session = Depends(get_db)):
    user_id = profile_data.user_id
    profile = db.query(UserProfile).filter(UserProfile.id == user_id).first()
    if not profile:
        profile = UserProfile(
            id=user_id,
            username=profile_data.username or user_id,
        )

    if profile_data.username:
        profile.username = profile_data.username
    profile.xtream_portal = profile_data.xtream_portal
    profile.xtream_username = profile_data.xtream_username
    profile.xtream_password = profile_data.xtream_password
    profile.plex_token = profile_data.plex_token
    profile.plex_server_url = profile_data.plex_server_url

    db.add(profile)
    db.commit()
    return {
        "status": "success",
        "message": "Profile configuration synchronized successfully.",
    }


@app.get("/api/dashboard")
def get_dashboard(
    x_rushy_user_id: str = Header(..., alias="x-rushy-user-id"),
    db: Session = Depends(get_db),
):
    validate_user_access(x_rushy_user_id)

    favorites = (
        db.query(MediaStream)
        .filter(
            MediaStream.tenant_id == x_rushy_user_id,
            MediaStream.is_favorite == True,  # noqa: E712
            MediaStream.is_hidden == False,  # noqa: E712
        )
        .all()
    )

    live = (
        db.query(MediaStream)
        .filter(
            MediaStream.tenant_id == x_rushy_user_id,
            MediaStream.stream_type == "live",
            MediaStream.is_hidden == False,  # noqa: E712
        )
        .limit(50)
        .all()
    )

    return {
        "favorites": [model_to_dict(f) for f in favorites],
        "live_tv": [model_to_dict(l) for l in live],
    }


@app.post("/api/media/toggle-visibility")
def toggle_visibility(
    payload: ToggleVisibilityRequest,
    x_rushy_user_id: str = Header(..., alias="x-rushy-user-id"),
    db: Session = Depends(get_db),
):
    validate_user_access(x_rushy_user_id)

    item = (
        db.query(MediaStream)
        .filter(
            MediaStream.tenant_id == x_rushy_user_id,
            MediaStream.stream_id == payload.stream_id,
        )
        .first()
    )

    if not item:
        raise HTTPException(status_code=404, detail="Target stream reference missing.")

    if payload.action == "hide":
        item.is_hidden = payload.state
    elif payload.action == "favorite":
        item.is_favorite = payload.state
    else:
        raise HTTPException(status_code=400, detail="Invalid action. Use 'hide' or 'favorite'.")

    db.commit()
    return {"status": "updated"}


search_router = APIRouter()
gemini_service = GeminiMediaService()


@search_router.get("/api/search")
def smart_search(
    q: str = Query(..., description="The conversational or direct voice query"),
    x_rushy_user_id: str = Header(..., alias="x-rushy-user-id"),
    db: Session = Depends(get_db),
):
    """
    Intelligent Search Hub: Combines exact database match filters,
    Gemini semantic interpretation, and RapidFuzz near-match auto-recovery.
    """
    validate_user_access(x_rushy_user_id)

    if not q or not q.strip():
        return {"exact_matches": [], "near_matches": [], "ai_analysis": {}}

    all_streams = (
        db.query(MediaStream)
        .filter(
            MediaStream.tenant_id == x_rushy_user_id,
            MediaStream.is_hidden == False,  # noqa: E712
        )
        .all()
    )

    if not all_streams:
        return {"exact_matches": [], "near_matches": [], "ai_analysis": {}}

    titles_list = [stream.title for stream in all_streams]
    exact_matches: list[dict[str, Any]] = []
    near_matches: list[tuple[dict[str, Any], float]] = []
    exact_ids: set[int] = set()
    near_ids: set[int] = set()

    normalized_query = q.lower().strip()
    for stream in all_streams:
        title_lower = stream.title.lower()
        if normalized_query == title_lower:
            stream_data = model_to_dict(stream)
            exact_matches.append(stream_data)
            exact_ids.add(stream.id)
        elif normalized_query in title_lower:
            stream_data = model_to_dict(stream)
            near_matches.append((stream_data, 90.0))
            near_ids.add(stream.id)

    is_conversational = len(normalized_query.split()) > 2
    ai_keywords: list[str] = []
    probable_title = ""

    if is_conversational and settings.gemini_api_key:
        ai_payload = gemini_service.translate_natural_search(normalized_query)
        probable_title = ai_payload.get("probable_title", "")
        keywords_str = ai_payload.get("keywords", "")
        ai_keywords = [keyword.strip().lower() for keyword in keywords_str.split(",") if keyword.strip()]

    fuzzy_results = process.extract(
        q,
        titles_list,
        scorer=fuzz.WRatio,
        limit=15,
        score_cutoff=65.0,
    )

    for matched_title, score, _ in fuzzy_results:
        for stream in all_streams:
            if stream.title == matched_title and stream.id not in exact_ids and stream.id not in near_ids:
                near_matches.append((model_to_dict(stream), float(score)))
                near_ids.add(stream.id)

    if ai_keywords:
        for stream in all_streams:
            if stream.id in exact_ids or stream.id in near_ids:
                continue

            stream_title_lower = stream.title.lower()
            match_score = 0
            for keyword in ai_keywords:
                if keyword in stream_title_lower:
                    match_score += 25

            if match_score > 0:
                near_matches.append((model_to_dict(stream), float(min(match_score + 50, 95))))
                near_ids.add(stream.id)

    near_matches.sort(key=lambda item: item[1], reverse=True)
    sorted_near_list = [item[0] for item in near_matches]

    return {
        "exact_matches": exact_matches,
        "near_matches": sorted_near_list[:10],
        "ai_analysis": {
            "is_conversational": is_conversational,
            "detected_keywords": ai_keywords,
            "probable_title": probable_title,
        },
    }


app.include_router(search_router)
