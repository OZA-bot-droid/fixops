#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/dev.py init
python3 scripts/dev.py db-up
python3 scripts/dev.py db-check
