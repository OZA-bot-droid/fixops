#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
java scripts/GenerateJwtKeys.java "${1:-local-secrets/jwt}"
