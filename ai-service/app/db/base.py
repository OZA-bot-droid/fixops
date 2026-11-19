from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Registry for AI-owned SQLAlchemy tables only."""
