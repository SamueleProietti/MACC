import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

class Base(DeclarativeBase):
    pass

_engine = None
_SessionLocal = None

def init_db():
    global _engine, _SessionLocal
    db_url = os.getenv("DB_URL")
    if not db_url:
        raise RuntimeError("DB_URL env var is missing")

    _engine = create_engine(db_url, pool_pre_ping=True)
    _SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=_engine)
    return _engine

def get_session():
    if _SessionLocal is None:
        raise RuntimeError("DB not initialized (call init_db() first)")
    return _SessionLocal()
