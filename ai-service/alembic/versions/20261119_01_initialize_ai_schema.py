"""Initial empty revision: ai_db and vector extension are bootstrapped by Postgres.

Revision ID: 20261119_01
Revises:
"""
from typing import Sequence, Union

revision: str = "20261119_01"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # No AI domain tables exist in the source project yet.
    pass


def downgrade() -> None:
    pass
