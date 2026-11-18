#!/usr/bin/env bash
set -euo pipefail
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD first}"
docker compose -f compose.local.yml config --quiet
docker compose -f compose.local.yml up -d postgres
bash scripts/verify-vector.sh
