# FixOps – 통합 Docker 및 로컬 개발 가이드

**실행 파일:** `compose.yml` 하나, 환경변수 템플릿 `.env.example` 하나. 개발자별 `.env` 파일은 **동일한 이름이지만 내용은 별도**입니다.

## 최초 실행

1. Java 21 및 Docker Desktop 설치. `python scripts/dev.py init`은 비밀번호가 들어간 `.env`와 RSA-3072 JWT PEM 키를 생성합니다. Python 없이 진행하려면 `.env.example`을 `.env`로 복사하여 비밀번호를 채우고 `java scripts/GenerateJwtKeys.java` 실행.
2. `docker compose config --quiet`로 환경변수를 검증합니다.
3. **개인 DB만:** `docker compose up -d postgres`. Java 서비스는 각 IDE에서 `python scripts/dev.py run member` 등으로 실행할 수 있습니다. (`DB_URL`/`DB_USER`/`DB_PASSWORD`가 IDE에 전달되어야 합니다.)
4. **전체 통합:** `docker compose build && docker compose up -d`. 프론트: http://127.0.0.1:8088
5. `docker compose ps`, `docker compose logs -f member asset workorder inventory ai-migrate` 로 Flyway 수행 결과를 확인합니다.
6. 종료: `docker compose down` (**데이터 유지**). **`down -v`는 데이터를 삭제하므로 금지**.

## 공용 DB (팀장 컴퓨터)

- 팀장 컴퓨터에서 `.env`의 `DB_BIND_IP`에 실제 LAN IP(예: `192.168.0.84`)를 설정하여 `docker compose up -d postgres`. Windows 방화벽에서 팀원 IP만 허용합니다. 개인 PC에서는 자기 `.env`의 DB 비밀번호와 호스트를 별도로 지정해야 합니다.
- 팀원은 자신의 컴퓨터에서 `DB_URL=jdbc:postgresql://192.168.0.84:5432/<자기서비스_db>` 등의 IDE 환경변수를 사용하며 개인 앱을 실행합니다. 팀장 `.env`를 공유하지 않습니다. 공용 DB와 개인 DB의 버전은 별도 관리해야 합니다.
- Docker 안에서는 DB 호스트명이 `postgres`, IDE 실행에서는 `127.0.0.1` 또는 팀장 IP입니다. 127.0.0.1로 지정된 `DB_BIND_IP`는 LAN에서 접속되지 않습니다.

## Flyway 정책

- `member-service`는 이미 도메인 DDL이 포함된 `V1__member_auth_org.sql`을 사용합니다.
- `asset-service`, `workorder-service`, `inventory-service`의 `V1__initialize_service.sql`은 **초기화 표시용 SELECT 1**입니다. 아직 도메인 모델 및 테이블이 정의되지 않았으므로 임의의 도메인 테이블을 만들지 않았습니다. 각 담당자가 실제 스키마를 `V2__*.sql`부터 추가해야 합니다.
- 서비스별 DB는 분리되어 독립적인 `flyway_schema_history`를 사용하며, 이미 적용한 V1/V2를 수정하지 않고 다음 버전을 추가합니다. `ddl-auto=validate`, `baseline-on-migrate=false`.
- AI 서비스는 Python 3.12 / SQLAlchemy 2 / Alembic을 사용합니다. `ai-migrate`가 `alembic upgrade head`에 성공해야 `ai` 컨테이너가 시작됩니다. `ai_db`는 별도의 `alembic_version`으로 관리합니다.
- 현재 초기 revision (`20261119_01`)은 **빈 마이그레이션**입니다. AI 도메인 엔티티가 아직 없기 때문입니다. 실제 모델 추가 시 `ai-service/app/models` 및 `alembic/env.py`에 등록한 후 `alembic revision --autogenerate`를 생성·검토·커밋합니다.
- `vector` 확장 생성은 기존 PostgreSQL bootstrap이 담당합니다. Alembic에서 중복 생성하지 않습니다. 기존 DB 볼륨이면 확장 존재 여부를 먼저 검증합니다.

## AI Alembic 관리 명령어

```bash
docker compose run --rm ai-migrate alembic heads
docker compose run --rm ai-migrate alembic current
docker compose run --rm ai-migrate alembic upgrade head
# 실제 AI 모델을 정의하고 alembic/env.py에서 import 등록한 다음:
docker compose run --rm ai-migrate alembic revision --autogenerate -m "add ai tables"
```

- `docker compose up -d`에서는 의존성에 따라 PostgreSQL → `ai-migrate` 완료 → `ai` 순으로 시작합니다.
- 공용 `ai_db`에는 마이그레이션 실행 담당자를 한 명으로 지정하고, **적용된 revision 수정과 병렬 업그레이드를 금지**합니다.
- 로컬 IDE에서 AI를 실행하려면 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`를 설정해야 합니다. Compose에서 설정되는 DB 호스트 `postgres`는 로컬 IDE에서 접근할 수 없습니다.
- 마이그레이션 성공은 도메인 기능 완성을 의미하지 않습니다. 자세한 사항은 `ai-service/README.md`를 참고하세요.

## 현재 스캐폴드 제약

- `frontend/package-lock.json`이 ZIP에 없어 `npm ci` 기반 프론트 Docker 이미지가 현재 빌드되지 않습니다. `cd frontend && npm install --package-lock-only`, 이후 lock 커밋 필요.
- Java Gradle Wrapper도 ZIP에 없어 IDE에서 `gradle` 설치 또는 `scripts/generate-gradle-wrappers.sh` 실행 후 wrapper 커밋이 필요합니다. Dockerfile은 자체 Gradle 이미지로 빌드합니다.
- AI/MCP/도메인 서비스는 일부 스캐폴드로, **DB/Flyway 구성 완료가 전체 기능 구현 완료를 뜻하지 않습니다**. 로컬 Docker Engine, 네트워크 연결, Gradle/npm 의존성 다운로드가 필요합니다.
- `scripts/GenerateJwtKeys.java`는 **Java 21만으로** PKCS#8 개인키·X.509 공개키 PEM을 생성합니다. OpenSSL 별도 설치 불필요. 기존 키 자동 덮어쓰기는 하지 않습니다. 실제 운영 키는 시크릿 관리 및 접근권한이 필수입니다.

## 정리 내역과 보존 원칙

- 삭제: 구형 Compose 4개, 구형 .env 템플릿 3개, 중복 `config/stack.env`, 중복 `README_DOCKER.md`, 중복 DB 초기화 SQL, 이미 적용된 안내용 `patches/`.
- 유지: `versions.env`는 `scripts/validate-env.py` 및 Gradle wrapper 생성 스크립트의 기준 버전값입니다. `scripts/`는 실행 준비·검증·개발 편의 기능이 있으므로 일괄 삭제하면 안 됩니다.
- DB는 처음 **빈 볼륨**으로 시작할 때만 `infra/postgres/bootstrap/01-bootstrap.sh`가 역할과 DB를 생성합니다. 기존 볼륨에는 비밀번호/초기화 SQL이 자동 재적용되지 않습니다.
