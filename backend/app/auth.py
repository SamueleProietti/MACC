import os
from functools import wraps
from flask import request, jsonify, g

import firebase_admin
from firebase_admin import auth as fb_auth, credentials

_firebase_initialized = False

def init_firebase():
    """
    In Cloud Run useremo Application Default Credentials (service account).
    In locale puoi usare una serviceAccountKey.json (opzionale), oppure anche solo ADC se hai gcloud auth.
    """
    global _firebase_initialized
    if _firebase_initialized:
        return

    # Opzione A: service account key (dev)
    sa_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if sa_path:
        cred = credentials.Certificate(sa_path)
        firebase_admin.initialize_app(cred)
    else:
        # Opzione B: Application Default Credentials (GCP / Cloud Run)
        firebase_admin.initialize_app()

    _firebase_initialized = True

def require_auth(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        init_firebase()

        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return jsonify(error="missing_bearer_token"), 401

        token = header.split(" ", 1)[1].strip()
        try:
            decoded = fb_auth.verify_id_token(token)
        except Exception:
            return jsonify(error="invalid_token"), 401

        g.uid = decoded.get("uid")
        if not g.uid:
            return jsonify(error="invalid_token_no_uid"), 401

        return fn(*args, **kwargs)
    return wrapper
