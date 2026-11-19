#!/usr/bin/env bash
set -euo pipefail
docker compose -f compose.yml config --quiet
docker compose -f compose.yml up -d postgres
bash scripts/verify-vector.sh
