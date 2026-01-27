import os
import uuid
from datetime import timedelta

from google.cloud import storage

GCS_BUCKET = os.getenv("GCS_BUCKET", "")
GCS_PUBLIC = os.getenv("GCS_PUBLIC", "false").lower() == "true"


def upload_profile_photo_to_bucket(uid: str, file_storage) -> str:
    """
    Upload su Google Cloud Storage.

    Ritorna un URL utilizzabile dall'app:
    - Se GCS_PUBLIC=true: URL pubblico "classico" (richiede bucket pubblico via IAM, con UBLA).
    - Altrimenti: signed URL temporaneo (1 ora).
    """
    if not GCS_BUCKET:
        raise RuntimeError("GCS_BUCKET env var is missing")

    client = storage.Client()
    bucket = client.bucket(GCS_BUCKET)

    filename = getattr(file_storage, "filename", None) or "photo.jpg"
    ext = os.path.splitext(filename)[1].lower()
    if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
        ext = ".jpg"

    object_name = f"users/{uid}/profile_{uuid.uuid4().hex}{ext}"
    blob = bucket.blob(object_name)

    content_type = getattr(file_storage, "mimetype", None) or "application/octet-stream"

    # assicura inizio stream
    stream = getattr(file_storage, "stream", None) or file_storage
    try:
        stream.seek(0)
    except Exception:
        pass

    blob.upload_from_file(stream, content_type=content_type)

    # UBLA attivo: NON usare blob.make_public()
    if GCS_PUBLIC:
        # URL pubblica "classica"
        return f"https://storage.googleapis.com/{GCS_BUCKET}/{object_name}"

    # Signed URL (1 ora) - utile se bucket privato
    return blob.generate_signed_url(
        expiration=timedelta(hours=1),
        method="GET",
        version="v4",
    )
