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

# Gradle version consistency validation
versions = {}
for line in (R / 'versions.env').read_text().splitlines():
    line = line.strip()

    if line and not line.startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        versions[key.strip()] = value.strip()
gradle_version = versions['GRADLE_VERSION']
java_version = versions['JAVA_MAJOR']
spring_boot_version = versions['SPRING_BOOT_VERSION']
java_services = [
    'gateway',
    'member-service',
    'asset-service',
    'workorder-service',
    'inventory-service',
]
for service in java_services:
    base = R / service
    wrapper = (
        base / 'gradle/wrapper/gradle-wrapper.properties'
    ).read_text()
    dockerfile = (base / 'Dockerfile').read_text()
    build = (base / 'build.gradle').read_text()
    if f'gradle-{gradle_version}-bin.zip' not in wrapper:
        errors.append(
            f'{service}: Gradle Wrapper version mismatch'
        )
    if f'gradle:{gradle_version}-jdk{java_version}' not in dockerfile:
        errors.append(
            f'{service}: Docker Gradle version mismatch'
        )
    if f"version '{spring_boot_version}'" not in build:
        errors.append(
            f'{service}: Spring Boot version mismatch'
        )
    if f'JavaLanguageVersion.of({java_version})' not in build:
        errors.append(
            f'{service}: Java toolchain version mismatch'
        )
    if 'junit-platform-launcher' not in build:
        errors.append(
            f'{service}: JUnit Platform Launcher missing'
        )
    if not (
        base / 'gradle/wrapper/gradle-wrapper.jar'
    ).is_file():
        errors.append(
            f'{service}: Gradle Wrapper JAR missing'
        )
if errors: raise SystemExit('\n'.join(errors))
print('OK: one Compose, one env template, 4 Java Flyway services')
