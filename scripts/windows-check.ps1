$ErrorActionPreference = 'Continue'
Write-Host 'Java' -ForegroundColor Cyan; java -version
Write-Host 'Node' -ForegroundColor Cyan; node --version
Write-Host 'npm' -ForegroundColor Cyan; npm --version
Write-Host 'Python' -ForegroundColor Cyan; py -3.12 --version
Write-Host 'Docker' -ForegroundColor Cyan; docker --version; docker compose version
Write-Host 'Run: py -3.12 scripts/validate-env.py --root .' -ForegroundColor Green
