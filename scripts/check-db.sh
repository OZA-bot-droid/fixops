#!/usr/bin/env bash
set -Eeuo pipefail
mode="${1:-local}"
[[ "$mode" == local || "$mode" == integration ]] || { echo "usage: $0 local|integration"; exit 2; }
file="compose.db.$mode.yml"
envfile=".env.db.$mode"
[[ -f "$envfile" ]] || { echo "Missing $envfile (copy .env.db.example first)"; exit 2; }
docker compose --env-file "$envfile" -f "$file" exec -T postgres \
  psql -U postgres -d postgres -Atc "SELECT datname FROM pg_database WHERE datname IN ('member_db','asset_db','workorder_db','inventory_db','ai_db') ORDER BY datname;"
docker compose --env-file "$envfile" -f "$file" exec -T postgres \
  psql -U postgres -d ai_db -Atc "SELECT extversion FROM pg_extension WHERE extname='vector';"
