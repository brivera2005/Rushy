from sqlalchemy import Boolean, Column, ForeignKey, Integer, String, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

from app.config import settings

engine = create_engine(
    settings.database_url,
    connect_args={"check_same_thread": False},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class UserProfile(Base):
    __tablename__ = "user_profiles"

    id = Column(String, primary_key=True, index=True)
    username = Column(String, unique=True, index=True)
    xtream_portal = Column(String, nullable=True)
    xtream_username = Column(String, nullable=True)
    xtream_password = Column(String, nullable=True)
    plex_token = Column(String, nullable=True)
    plex_server_url = Column(String, nullable=True)


class MediaStream(Base):
    __tablename__ = "media_streams"

    id = Column(Integer, primary_key=True, autoincrement=True)
    tenant_id = Column(String, ForeignKey("user_profiles.id"))
    stream_id = Column(String, index=True)
    title = Column(String, index=True)
    stream_type = Column(String)  # 'live', 'movie', 'series', 'plex'
    category_id = Column(String)
    logo_url = Column(String, nullable=True)
    is_hidden = Column(Boolean, default=False)
    is_favorite = Column(Boolean, default=False)
    tmdb_id = Column(String, nullable=True)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)
