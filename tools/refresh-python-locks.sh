#!/usr/bin/env bash
set -euo pipefail
for service in ai-service mcp-server; do
  echo "==> $service"
  (cd "$service" && uv lock && uv lock --check && uv sync --locked --group dev && uv run --locked python -m compileall -q app)
done
printf '
Both uv.lock files regenerated and syntax checked. Commit BOTH lockfiles.
'
