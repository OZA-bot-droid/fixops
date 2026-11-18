# FixOps v1.0 — LOCAL / INTEGRATION / DEMO DB environment pack

**Existing v1.0 project scaffold overlay, not a replacement repo.** Copy these files to the repository root, preserving `docs/`, services, and their build configuration. The two compose files here are DB-only; do not merge them with the old `compose.local.yml` without deciding how volumes and ports coexist.

## Chosen operating contract

- **LOCAL**: each team member runs `compose.db.local.yml` on their own PC. `127.0.0.1:5432`; 5 logical DBs; each person's data independent. IntelliJ/VS Code run apps.
- **INTEGRATION**: only team leader's PC `192.168.0.84` runs `compose.db.integration.yml`. Team members connect to DB by LAN IP **only for integration verification**. Full service routing still needs reachable IDE processes or centralized service execution. Never expose DB to public internet.
- **DEMO/AWS**: EC2 Docker Compose for apps; Amazon RDS PostgreSQL 16 and `vector 0.8.2` **only after RDS engine-version support is checked**; S3 for files, ALB/ACM HTTPS, existing domain. No DB container on EC2 in AWS configuration.

## Bootstrap (all 5 developers)

1. Verify Docker Desktop/Engine and Compose v2 installed. Obtain a recent **main/develop** scaffold. Ensure host TCP 5432 free.
2. Copy `.env.db.example` → `.env.db.local`; replace ALL placeholder passwords with independent random secrets. **Never commit** actual `.env.db.*` files.
3. Run `docker compose --env-file .env.db.local -f compose.db.local.yml up -d`.
4. Run `bash scripts/check-db.sh local`. Expected five DB names and extension version `0.8.2`.
5. IDE: set `DB_HOST=127.0.0.1`, `DB_PORT=5432`, `DB_PASSWORD=<your service DB password>`; service user and DB name from `config/service-db-configuration.md`.
6. Start your assigned Java/Python/Frontend service in IDE, run migrations, then test API. Other unavailable services may be mocked by contract. Never directly query another service's DB.

## Integration DB on leader PC only

1. Ensure local network IP is `192.168.0.84` (DHCP reservation recommended); verify LAN client isolation is not enabled. Keep Windows network profile private.
2. Windows Firewall inbound TCP 5432: allow **only team member device IPs**, no public port-forward. Optional PostgreSQL `pg_hba.conf` network/role restrictions require explicitly managed config; firewall is not a replacement for least-privilege role authentication. **Use strong, distinct passwords; plain LAN PostgreSQL without TLS is a short-lived trusted-network exception, not production-grade security.** Prefer TLS or secure tunnel if using untrusted networks.
3. `.env.db.example` → `.env.db.integration`, use DIFFERENT secrets from local; do not distribute superuser password to developers.
4. `docker compose --env-file .env.db.integration -f compose.db.integration.yml up -d`.
5. If Docker Desktop rejects binding to `192.168.0.84`, use `INTEGRATION_BIND_IP=0.0.0.0` only AFTER firewall restrictions; verify with team members. This increases network exposure.
6. On other PCs: `powershell -File scripts/check-integration.ps1`; then test actual DB login with assigned per-service user using DBeaver/psql.
7. **Single migration owner per database** and integration migrations only from an agreed `develop` commit. Back up before schema migrations; do not run destructive automated tests against shared DB.
8. Team leader PC must stay awake during agreed integration windows. Assign a backup operator and record backup restoration instructions.

## Five DB ownership table

| Service owner | DB | DB login |
|---|---|---|
| 오좌언 Member | member_db | member_app |
| 이도형 Asset | asset_db | asset_app |
| 박천보 WorkOrder | workorder_db | workorder_app |
| 이도관 Inventory | inventory_db | inventory_app |
| 하진철 AI | ai_db | ai_app |

`Gateway` and `MCP Server` have no direct DB access. The PostgreSQL extension `vector` installs **only in ai_db**. Python AI's Alembic migrations own AI schema; Flyway owns Java DB schemas.

## Lifecycle / reset safety

- Stop without deletion: `docker compose --env-file .env.db.local -f compose.db.local.yml down`.
- The bootstrap `01-bootstrap.sh` runs **only on the first initialization of an empty data volume**. Changing DB password values later in `.env.db.*` will NOT update already-created PostgreSQL roles. Use supervised SQL `ALTER ROLE` when rotating secrets.
- **Never** run `down -v` on INTEGRATION. Local reset, when explicitly desired, requires backing up or accepting permanent data loss.
- The original scaffold's `infra/postgres/init/01-databases.sql` is **not mounted** by these Compose files, avoiding duplicate DB creation. Do not mount both bootstrap locations.
- LOCAL and INTEGRATION compose projects use **different volume names**. Do not run both on the same host port 5432 simultaneously.

## DEMO/AWS

- Keep `DB_HOST`, `DB_PORT`, credentials environment-driven. Point all services at RDS endpoint with proper database name and non-superuser login. Apply Flyway/Alembic there from a controlled release artifact; do not run Docker bootstrap scripts against RDS.
- Explicitly check `SELECT version FROM pg_available_extension_versions WHERE name='vector'` **and** `SELECT extversion FROM pg_extension WHERE extname='vector'`. Provision `vector` 0.8.2 inside `ai_db` only. The precise RDS 16 minor release must support 0.8.2.
- Before actual production deployment enforce TLS certificate validation, secret store, restricted security group, snapshots, private S3, and no real resident personal data in demonstration.

## Validation boundaries

This overlay provides **real Compose + bootstrap + version verification scripts**. It has NOT performed a live Docker container run, client authentication, or AWS deployment in this environment. Java/Python/Frontend builds are separate work under the existing scaffold and remain unverified.
