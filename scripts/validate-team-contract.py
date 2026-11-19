from pathlib import Path
R=Path(__file__).resolve().parents[1]
errors=[]
for f in ['compose.yml','.env.example','infra/postgres/bootstrap/01-bootstrap.sh','frontend/package.json','frontend/src/app/App.jsx','versions.env','scripts/dev.py','scripts/GenerateJwtKeys.java']:
    if not (R/f).exists(): errors.append(f'Missing {f}')
compose=(R/'compose.yml').read_text()
if '${DB_BIND_IP:-127.0.0.1}' not in compose: errors.append('DB bind should default to loopback')
if len(list(R.glob('compose*.yml')))!=1: errors.append('Exactly one compose*.yml required')
if len(list(R.glob('.env*.example')))!=1: errors.append('Exactly one .env*.example required')
for service in ['member','asset','workorder','inventory']:
    base=R/(service+'-service')
    if not list((base/'src/main/resources/db/migration').glob('V*.sql')): errors.append('Flyway migration missing: '+service)
    for f in ['build.gradle','src/main/resources/application.yml']:
        if 'flyway' not in (base/f).read_text(): errors.append('Flyway config missing: '+str(base/f))
if errors: raise SystemExit('\n'.join(errors))
print('OK: one Compose, one env template, 4 Java Flyway services')
