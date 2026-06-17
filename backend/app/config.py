"""Environment variables and security matrix for Rushy backend."""

import os
from functools import lru_cache


class Settings:
    """Centralized configuration loaded from environment variables."""

    def __init__(self) -> None:
        self.app_name: str = os.getenv("RUSHY_APP_NAME", "Rushy Orchestration Engine")
        self.debug: bool = os.getenv("RUSHY_DEBUG", "false").lower() in ("1", "true", "yes")
        self.database_url: str = os.getenv("RUSHY_DATABASE_URL", "sqlite:///./rushy.db")
        self.host: str = os.getenv("RUSHY_HOST", "0.0.0.0")
        self.port: int = int(os.getenv("RUSHY_PORT", "8000"))

        # Security matrix
        self.jwt_secret: str = os.getenv("RUSHY_JWT_SECRET", "change-me-in-production")
        self.jwt_algorithm: str = os.getenv("RUSHY_JWT_ALGORITHM", "HS256")
        self.jwt_expire_minutes: int = int(os.getenv("RUSHY_JWT_EXPIRE_MINUTES", "1440"))
        self.require_user_header: bool = os.getenv(
            "RUSHY_REQUIRE_USER_HEADER", "true"
        ).lower() in ("1", "true", "yes")
        self.allowed_origins: list[str] = [
            origin.strip()
            for origin in os.getenv("RUSHY_ALLOWED_ORIGINS", "*").split(",")
            if origin.strip()
        ]
        allowed_users = os.getenv("RUSHY_ALLOWED_USER_IDS", "")
        self.allowed_user_ids: list[str] = [
            user_id.strip() for user_id in allowed_users.split(",") if user_id.strip()
        ]

        # Worker schedule
        self.plex_prune_cron_hour: int = int(os.getenv("RUSHY_PLEX_PRUNE_HOUR", "3"))
        self.plex_prune_cron_minute: int = int(os.getenv("RUSHY_PLEX_PRUNE_MINUTE", "0"))

        # External API timeouts (seconds)
        self.http_timeout: int = int(os.getenv("RUSHY_HTTP_TIMEOUT", "30"))

        # Google Gemini (conversational search expansion)
        self.gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")

        # Google Gemini (optional — conversational search interpretation)
        self.gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")

    def is_user_allowed(self, user_id: str) -> bool:
        if not self.allowed_user_ids:
            return True
        return user_id in self.allowed_user_ids


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
