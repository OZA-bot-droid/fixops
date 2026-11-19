# FixOps AI Service — Alembic

FastAPI `/health` only at present. RAG, LangChain, MCP client, vector-backed models and org filters are future work.

- DB: `ai_db`, role: `ai_app` (created by `infra/postgres/bootstrap/01-bootstrap.sh` on **first** PostgreSQL volume initialization).
- Schema migrations: SQLAlchemy 2 + Alembic; `alembic_version` belongs to `ai_db` only.
- `alembic/versions/20261119_01_initialize_ai_schema.py` is **intentionally empty** because the source has no AI domain tables yet.
- `vector` extension is installed by Postgres bootstrap, **not** an Alembic revision. Existing volumes must be verified separately.
- Compose starts `ai-migrate` as a one-off job before `ai`. Failure blocks AI startup.

At the repository root:

```sh
docker compose up -d postgres
docker compose run --rm ai-migrate alembic current
docker compose run --rm ai-migrate alembic upgrade head
docker compose run --rm ai-migrate alembic heads
docker compose up -d ai
```

When implementing a real `app/models/*.py` SQLAlchemy model, import it in `alembic/env.py` so autogenerate can inspect `Base.metadata`. **Review** the generated migration before committing:

```sh
docker compose run --rm ai-migrate alembic revision --autogenerate -m "add ai tables"
docker compose run --rm ai-migrate alembic upgrade head
```

On a shared DB, only one developer/team integration job should apply migrations at a time. Commit the revision files; never rewrite a revision that was already applied. On an existing populated `ai_db`, compare schemas before running or stamping anything.

Local non-Docker usage: set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`; run `alembic upgrade head` from `ai-service/` after installing requirements.
