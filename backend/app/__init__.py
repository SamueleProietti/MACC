from flask import Flask
from .routes.health import bp as health_bp
from .routes.profile import bp as profile_bp
from .db import init_db, Base
import os

def create_app():
    app = Flask(__name__)

    # init db + create tables (per dev)
    engine = init_db()
    from . import models  # noqa: F401 (importa i modelli)
    Base.metadata.create_all(bind=engine)

    app.register_blueprint(health_bp)
    app.register_blueprint(profile_bp, url_prefix="/v1")
    os.makedirs("/app/uploads", exist_ok=True)
    return app
