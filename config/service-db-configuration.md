# IDE DB config contract

| Service | DB | user | Host env | Password env |
|---|---|---|---|---|
| Member | member_db | member_app | DB_HOST | DB_PASSWORD |
| Asset | asset_db | asset_app | DB_HOST | DB_PASSWORD |
| WorkOrder | workorder_db | workorder_app | DB_HOST | DB_PASSWORD |
| Inventory | inventory_db | inventory_app | DB_HOST | DB_PASSWORD |
| Python AI | ai_db | ai_app | DB_HOST | DB_PASSWORD |

All IDE services use `DB_HOST=127.0.0.1` for LOCAL or `DB_HOST=192.168.0.84` for INTEGRATION.
Java: `jdbc:postgresql://${DB_HOST:127.0.0.1}:${DB_PORT:5432}/<service_db>`.
Python: construct SQLAlchemy URL with correct driver and env vars, avoid printing password.
Gateway / MCP: no direct database; do not connect them to application DBs.

Local development: IDE services usually run on their own computer and connect ONLY to their own DB.
Integration: run or expose all services on the SAME integration host (or use explicit reachable host IPs), not `localhost` on each developer's computer. DB connection is independent of API routing.

Flyway and Alembic migrations run under the DB-owning service. `ddl-auto=validate` is required.
Avoid direct cross-service SQL. Run integration migrations from a known integration commit in a coordinated order. Do not run incompatible feature branches' migration sets concurrently on the integration DB.
