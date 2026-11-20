$ErrorActionPreference = 'Stop'
foreach ($service in @('ai-service', 'mcp-server')) {
    Push-Location $service
    try {
        uv lock
        if ($LASTEXITCODE -ne 0) { throw "uv lock failed: $service" }
        uv lock --check
        if ($LASTEXITCODE -ne 0) { throw "uv lock --check failed: $service" }
        uv sync --locked --group dev
        if ($LASTEXITCODE -ne 0) { throw "uv sync failed: $service" }
        uv run --locked python -m compileall -q app
        if ($LASTEXITCODE -ne 0) { throw "compileall failed: $service" }
    } finally { Pop-Location }
}
Write-Host 'Both uv.lock files regenerated. Commit both lockfiles.'
