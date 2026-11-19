param([switch]$SkipDatabase)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$py = if (Get-Command py -ErrorAction SilentlyContinue) {'py'} elseif (Get-Command python -ErrorAction SilentlyContinue) {'python'} else {throw 'Python 3.12이 설치되지 않았습니다'}
if ($py -eq 'py') { & py -3.12 scripts/dev.py init } else { & python scripts/dev.py init }
if ($LASTEXITCODE -ne 0) { throw 'init 실패' }
if (-not $SkipDatabase) {
  if ($py -eq 'py') { & py -3.12 scripts/dev.py db-up } else { & python scripts/dev.py db-up }
  if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 시작 실패' }
}
Write-Host '개발환경 준비 완료. DB 준비 확인 후 각자의 서비스만 실행하세요.' -ForegroundColor Green
