param([string]$OutputDir = "local-secrets/jwt")
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
& java scripts/GenerateJwtKeys.java $OutputDir
if ($LASTEXITCODE -ne 0) { throw "JWT key creation failed" }
