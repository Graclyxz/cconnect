"""ORM models. Settings use a key-value table so adding a new setting never alters the
schema — the setting's type/default/description live in core.settings_defs, not in columns.

Column comments document intent (SQLite ignores them at the DB level, but they travel with
the model and apply on engines that support comments, e.g. Postgres)."""

from typing import Optional

from sqlalchemy.orm import Mapped, mapped_column

from core.db import Base


class Setting(Base):
    __tablename__ = "settings"

    key: Mapped[str] = mapped_column(
        primary_key=True,
        comment="Setting key; matches an entry in the code registry (settings_defs.SETTINGS)",
    )
    value: Mapped[Optional[str]] = mapped_column(
        nullable=True,
        comment="JSON-encoded override value; NULL means the code-defined default is used",
    )
    description: Mapped[str] = mapped_column(
        default="",
        comment="Human-readable description of the setting, synced from the code registry",
    )
