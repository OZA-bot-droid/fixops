#!/usr/bin/env python3
"""Offline prerequisite checks for the full FixOps Docker image build."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
errors=[]
for name in ('gateway','member-service','asset-service','workorder-service','inventory-service','frontend','ai-service','mcp-server'):
    f=root/name/'Dockerfile'
    if not f.is_file(): errors.append(f'Missing {f.relative_to(root)}')
if not (root/'frontend/package-lock.json').is_file():
    errors.append('Missing frontend/package-lock.json; run cd frontend && npm install --package-lock-only and commit it; Docker build uses npm ci')
for name in ('private_key.pem','public_key.pem'):
    if not (root/'local-secrets/jwt'/name).is_file():
        errors.append(f'Missing local-secrets/jwt/{name}; run scripts/generate-jwt-keys.ps1 (Windows) or .sh')
env=root/'.env'
if not env.is_file(): errors.append('Missing .env; copy .env.example and set secrets')
else:
    t=env.read_text(encoding='utf8')
    if 'REPLACE_' in t: errors.append('.env has placeholder secret values')
print('FixOps full Docker preflight:')
if errors:
    for e in errors: print(' - '+e)
    sys.exit(1)
print(' - static prerequisites available (Docker build still must be tested)')
