#!/usr/bin/env bash
set -Eeuo pipefail
# Invoked by official postgres entrypoint ONLY on first initialization of an empty data dir.
: "${MEMBER_DB_PASSWORD:?required}"
: "${ASSET_DB_PASSWORD:?required}"
: "${WORKORDER_DB_PASSWORD:?required}"
: "${INVENTORY_DB_PASSWORD:?required}"
: "${AI_DB_PASSWORD:?required}"

bootstrap_one() {
  local role="$1" database="$2" secret="$3"
  # psql :'<var>' quotes SQL literals safely; format %I/%L escapes identifiers/literals.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v role_name="$role" -v database_name="$database" -v role_password="$secret" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role_name', :'role_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role_name') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'role_name')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database_name') \gexec
SQL
}
bootstrap_one member_app member_db "$MEMBER_DB_PASSWORD"
bootstrap_one asset_app asset_db "$ASSET_DB_PASSWORD"
bootstrap_one workorder_app workorder_db "$WORKORDER_DB_PASSWORD"
bootstrap_one inventory_app inventory_db "$INVENTORY_DB_PASSWORD"
bootstrap_one ai_app ai_db "$AI_DB_PASSWORD"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ai_db \
  -c "CREATE EXTENSION IF NOT EXISTS vector VERSION '0.8.2';"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ai_db \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
