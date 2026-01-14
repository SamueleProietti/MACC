import os
import uuid

UPLOAD_DIR = os.getenv("UPLOAD_DIR", "/app/uploads")

def save_profile_photo(uid: str, file_storage) -> str:
    """
    DEV ONLY: salva nel filesystem del container.
    Ritorna una 'URL' finta che useremo per ora come riferimento.
    In Step 5 lo sostituiamo con upload su Google Cloud Storage e URL reale.
    """
    os.makedirs(UPLOAD_DIR, exist_ok=True)

    # estensione (best-effort)
    filename = file_storage.filename or "photo.jpg"
    ext = os.path.splitext(filename)[1].lower()
    if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
        ext = ".jpg"

    out_name = f"{uid}_{uuid.uuid4().hex}{ext}"
    out_path = os.path.join(UPLOAD_DIR, out_name)

    file_storage.save(out_path)

    # "URL" dev (non pubblico): giusto per salvarlo nel DB e testare il flusso
    return f"local://uploads/{out_name}"
