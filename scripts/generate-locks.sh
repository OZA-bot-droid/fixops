#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
(cd frontend && npm install --package-lock-only --ignore-scripts && npm ci && npm run build)
(cd ai-service && uv lock && uv sync --frozen --group dev && uv run --frozen pytest)
(cd mcp-server && uv lock && uv sync --frozen --group dev && uv run --frozen pytest)
# Generate Java wrapper first with `gradle wrapper --gradle-version 8.14.3`
echo 'Commit lockfiles and Gradle wrappers in feature/infra after successful checks'
