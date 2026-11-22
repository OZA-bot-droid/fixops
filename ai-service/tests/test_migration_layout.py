from alembic.config import Config
from alembic.script import ScriptDirectory
from app.db.base import Base


def test_initial_revision_is_discoverable():
    cfg = Config("alembic.ini")
    directory = ScriptDirectory.from_config(cfg)
    heads = directory.get_heads()
    assert heads == ["20261119_01"]
    assert directory.get_revision(heads[0]).down_revision is None


def test_ai_models_are_not_invented_in_skeleton():
    assert list(Base.metadata.tables) == []
