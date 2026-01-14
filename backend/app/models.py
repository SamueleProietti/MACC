from sqlalchemy import Column, Integer, String, Float, DateTime, Text, func
from .db import Base

class UserProfile(Base):
    __tablename__ = "user_profiles"
    id = Column(Integer, primary_key=True)
    firebase_uid = Column(String(128), unique=True, index=True, nullable=False)
    nickname = Column(String(80), nullable=True)
    photo_url = Column(Text, nullable=True)
    last_lat = Column(Float, nullable=True)
    last_lng = Column(Float, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=False)
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now(), nullable=False)
