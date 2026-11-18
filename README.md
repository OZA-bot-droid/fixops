# FixOps v1.0 팀 공통 개발환경 — 버전 계약 및 Overlay

> 본 ZIP은 **기존 FixOps v1.0 스캐폴드 위에 적용하는 설정 패치**입니다. 전체 실행 프로젝트를 새로 만든 것은 아닙니다. 기존 `docs` / 사용자 비밀정보 / 업무 구현 파일은 보존해야 합니다.

## 팀 합의 버전

| 구분 | 기준 | 잠금 상태 |
|---|---|---|
| Java | 21 LTS | Major 고정, 패치 각 플랫폼 검증 |
| Spring Boot | **4.0.9** | 공통 고정(실제 Gradle 컴파일 필요) |
| Spring Cloud | **2025.1.4** | 4.0.9 기반 출시 조합; 기존 2025.1.3 → 수정 |
| Gradle | **8.14.3** | wrapper 생성 후 검증/커밋, 미포함 시 비재현 |
| Node.js | **22.12.0** | `.nvmrc` |
| npm | **10.9.2** | npm 버전 통일 |
| Python | **3.12.x** | `.python-version`은 3.12, 가능하면 패치 통일 |
| PostgreSQL | 16 | Docker 이미지 / RDS 16 |
| pgvector | **0.8.2** | Docker `0.8.2-pg16-bookworm`, RDS 마이너 확인 |
| React / Vite / Tailwind | 19 / 7 / 4 | package-lock.json 커밋 전엔 범위 선언뿐 |
| LangChain / MCP / grpcio | D3 검증 | uv.lock 생성 전까지 미확정 |

## 적용 (기존 main/docs 덮어쓰지 말 것)

1. `develop`에서 새 `feature/infra`를 만든다. 이 overlay를 기존 스캐폴드 루트에 복사한다. **이미 수정한 파일은 반드시 Diff 확인**.
2. 각 Java `build.gradle`의 `org.springframework.boot` 버전 `4.0.9` 유지, Gateway의 `spring-cloud-dependencies`는 `2025.1.4`로 바꾼다. 추가 Java 모듈의 Cloud BOM도 동일하게.
3. 팀장 PC에서 `scripts/generate-gradle-wrappers.sh` 실행(임시 Gradle 필요). 생성된 **서비스별 wrapper JAR 포함** `gradlew*`와 `gradle/wrapper/*`를 커밋. Windows에서는 Gradle을 설치 후 서비스별 `gradle wrapper --gradle-version 8.14.3` 실행.
4. Node `nvm use 22.12.0`, `npm i -g npm@10.9.2`; `frontend`에서 `npm install --package-lock-only`, 그 후 `npm ci && npm run build` 성공 시 package-lock 커밋. `package.json`에 `packageManager: npm@10.9.2` 설정.
5. Python uv 설치 후 각각 `uv python install 3.12`, `uv lock`, `uv sync --frozen`, `uv run pytest` 실행. AI·MCP 기능 패키지는 D3 호환성 테스트로 정한 후 각각 `uv.lock` 커밋. `requirements.txt`와 pyproject.toml을 병행 관리하지 않는다.
6. `.env.example`을 로컬 `.env`로 복사하고 비밀값 변경. `docker compose -f compose.local.yml up -d postgres`; `bash scripts/verify-vector.sh`.
7. `python scripts/validate-env.py --root . --strict`로 필수 wrapper·lock 포함 검증. CI는 깨끗한 환경에서 `npm ci`, `./gradlew build`, `uv sync --frozen`, SQL vector check를 수행하도록 확장.

### Windows 주의
- PowerShell 검증: `powershell -ExecutionPolicy Bypass -File scripts/windows-check.ps1`.
- `scripts/*.sh`는 Git Bash/WSL에서 실행. Docker Desktop Linux container 모드.
- Windows 경로·개행 LF, UTF-8 통일. `.gitattributes`의 shell 스크립트 LF 유지.

## 버전 정책
- `versions.env`는 **팀 합의용 단일 정책표**이며 Java/Python/npm이 이를 자동으로 해석하지 않음. 각 서비스 설정 및 lock과 실제 버전이 맞는지 CI 확인.
- `package-lock.json`, `uv.lock`, `gradle-wrapper.jar`, 이미지 digest가 없으면 완전한 reproducible build라고 말하지 않는다.
- `pgvector 0.8.7` 볼륨을 `0.8.2` 이미지에 그대로 연결하지 않으며, DB 덤프/복원 등 별도 이전 절차 사용.
- RDS에서는 `ai_db`의 `pg_available_extension_versions`에 0.8.2가 있는지 먼저 확인.

## 공식 출처
- https://spring.io/blog/2026/10/23/spring-boot-4-0-9-available-now/
- https://spring.io/blog/2026/10/23/spring-cloud-2025-1-4-has-been-released/
- https://hub.docker.com/layers/pgvector/pgvector/0.8.2-pg16-bookworm/images/sha256-6d5c7b5aa3eb2098a2365f6d8cff723fded9e0e680757908b0b144b47f9c069c
- https://docs.astral.sh/uv/concepts/python-versions/
- https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-extensions.html

## 적용 자료
이 전체 스캐폴드는 초기 69개 파일에 공통 개발환경을 적용한 통합본입니다. 실제 앱 구현/lockfiles/Gradle wrappers는 아직 완성되지 않았습니다. 기존 저장소에 적용 시 `docs/`와 변경된 파일을 반드시 Diff 확인하세요.
