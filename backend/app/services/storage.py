import os
import uuid
from datetime import timedelta

from google.cloud import storage

GCS_BUCKET = os.getenv("GCS_BUCKET", "")
GCS_PUBLIC = os.getenv("GCS_PUBLIC", "false").lower() == "true"

def upload_profile_photo(uid: str, file_storage) -> str:
    """
    Upload su Google Cloud Storage.
    Ritorna un URL utilizzabile dall'app:
    - Se GCS_PUBLIC=true: URL pubblico
    - Altrimenti: signed URL (temporaneo)
    """
    if not GCS_BUCKET:
        raise RuntimeError("GCS_BUCKET env var is missing")

    client = storage.Client()
    bucket = client.bucket(GCS_BUCKET)

    filename = file_storage.filename or "photo.jpg"
    ext = os.path.splitext(filename)[1].lower()
    if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
        ext = ".jpg"

    object_name = f"users/{uid}/profile_{uuid.uuid4().hex}{ext}"
    blob = bucket.blob(object_name)

    content_type = file_storage.mimetype or "application/octet-stream"
    blob.upload_from_file(file_storage.stream, content_type=content_type)

    if GCS_PUBLIC:
        blob.make_public()
        return blob.public_url

    # Signed URL (1 ora): va bene per demo e privacy
    return blob.generate_signed_url(expiration=timedelta(hours=1), method="GET")
