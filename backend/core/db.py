"""SQLite data layer (SQLAlchemy 2.0, sync). Settings are read-heavy and write-rare,
so the store caches them in memory and only hits the DB on writes and at startup —
keeping reads off the event loop without an async driver."""

from pathlib import Path

from loguru import logger
from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import DeclarativeBase, sessionmaker

_DB_PATH = Path(__file__).resolve().parent.parent / "cconnect.db"

engine = create_engine(f"sqlite:///{_DB_PATH}", echo=False)
Session = sessionmaker(bind=engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def init_db() -> None:
    """Create any missing tables. New tables are picked up automatically; altering an
    existing table's columns isn't (the KV settings design avoids needing that)."""
    from core import models  # noqa: F401  (register models on Base before create_all)
    db_existed = _DB_PATH.exists()
    before = set(inspect(engine).get_table_names())
    Base.metadata.create_all(engine)
    after = set(inspect(engine).get_table_names())
    created = sorted(after - before)
    if not db_existed:
        logger.info(f"Created database {_DB_PATH.name} with tables: {', '.join(sorted(after))}")
    elif created:
        logger.info(f"Added new tables to {_DB_PATH.name}: {', '.join(created)}")
    else:
        logger.info(f"Database {_DB_PATH.name} ready ({len(after)} tables).")
