#!/usr/bin/env bash
set -euo pipefail
docker compose -f compose.yml exec -T postgres \
  psql -U postgres -d ai_db -X -v ON_ERROR_STOP=1 -c \
  "CREATE EXTENSION IF NOT EXISTS vector VERSION '0.8.2';"
version=$(docker compose -f compose.yml exec -T postgres \
  psql -U postgres -d ai_db -AtX -c "SELECT extversion FROM pg_extension WHERE extname='vector'")
if [[ "$version" != "0.8.2" ]]; then
 echo "FAIL vector version mismatch: $version" >&2
 exit 1
fi
echo "OK: ai_db vector=$version"
