from flask import Blueprint, jsonify, request, g
from sqlalchemy import select

from ..db import get_session
from ..models import UserProfile
from ..auth import require_auth
from ..services.storage import upload_profile_photo as upload_profile_photo_to_gcs  # ✅ alias per evitare collisione

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
    photo_url = body.get("photoUrl")  # ✅ opzionale: se vuoi salvarla anche da qui

    with get_session() as db:
        prof = db.execute(
            select(UserProfile).where(UserProfile.firebase_uid == uid)
        ).scalar_one_or_none()

        if not prof:
            prof = UserProfile(firebase_uid=uid)
            db.add(prof)

        # aggiorna campi (se None, lasciamo com’è)
        if nickname is not None:
            prof.nickname = nickname
        if lat is not None:
            prof.last_lat = lat
        if lng is not None:
            prof.last_lng = lng
        if photo_url is not None:
            prof.photo_url = photo_url

        db.commit()

    return jsonify(status="ok"), 200


@bp.post("/profile/me/photo")
@require_auth
def upload_profile_me_photo():  # ✅ rinominata per evitare collisione
    uid = g.uid

    if "photo" not in request.files:
        return jsonify(error="missing_file_field_photo"), 400

    file = request.files["photo"]
    if not file or file.filename == "":
        return jsonify(error="empty_filename"), 400

    # ✅ chiamiamo la funzione di storage (alias), NON la route
    photo_url = upload_profile_photo_to_gcs(uid, file)

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
