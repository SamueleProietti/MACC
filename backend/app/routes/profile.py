from flask import Blueprint, jsonify, request, g
from sqlalchemy import select

from ..db import get_session
from ..models import UserProfile
from ..auth import require_auth
from ..services.storage import save_profile_photo

bp = Blueprint("profile", __name__)


@bp.get("/profile/me")
@require_auth
def get_profile_me():
    uid = g.uid

    with get_session() as db:
        prof = db.execute(
            select(UserProfile).where(UserProfile.firebase_uid == uid)
        ).scalar_one_or_none()

        if not prof:
            return jsonify(error="profile_not_found"), 404

        return jsonify(
            uid=prof.firebase_uid,
            nickname=prof.nickname,
            photoUrl=prof.photo_url,
            lat=prof.last_lat,
            lng=prof.last_lng,
        ), 200


@bp.put("/profile/me")
@require_auth
def upsert_profile_me():
    uid = g.uid

    body = request.get_json(silent=True) or {}
    nickname = body.get("nickname")
    lat = body.get("lat")
    lng = body.get("lng")

    with get_session() as db:
        prof = db.execute(
            select(UserProfile).where(UserProfile.firebase_uid == uid)
        ).scalar_one_or_none()

        if not prof:
            prof = UserProfile(firebase_uid=uid)
            db.add(prof)

        # aggiorna campi
        prof.nickname = nickname
        prof.last_lat = lat
        prof.last_lng = lng

        db.commit()

    return jsonify(status="ok"), 200

@bp.post("/profile/me/photo")
@require_auth
def upload_profile_photo():
    uid = g.uid

    if "photo" not in request.files:
        return jsonify(error="missing_file_field_photo"), 400

    file = request.files["photo"]
    if file.filename == "":
        return jsonify(error="empty_filename"), 400

    photo_url = save_profile_photo(uid, file)

    with get_session() as db:
        prof = db.execute(
            select(UserProfile).where(UserProfile.firebase_uid == uid)
        ).scalar_one_or_none()

        if not prof:
            prof = UserProfile(firebase_uid=uid)
            db.add(prof)

        prof.photo_url = photo_url
        db.commit()

    return jsonify(photoUrl=photo_url), 201

