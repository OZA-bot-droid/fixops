# FixOps — v1.0 development scaffold

> 초기 골격(bootstrap)이며 완성된 서비스 구현·보안 검증·AWS 배포 결과가 아닙니다.
> 기존 GitHub `main/docs`는 삭제/덮어쓰지 않습니다.

## 팀 담당

| 담당 | 브랜치 | 소유 영역 |
|---|---|---|
| 오좌언 | `feature/member`, `feature/gateway`, `feature/frontend-common`, `feature/infra` | Member/Organization, Gateway, Front shared, CI/AWS |
| 이도형 | `feature/asset` | Asset/Location, Asset MCP tool |
| 박천보 | `feature/workorder` | WorkOrder/Booking, gRPC 9093, Booking MCP tool |
| 이도관 | `feature/inventory` | Inventory, Inventory MCP tool |
| 하진철 | `feature/ai` | FastAPI, MCP server, RAG, OTEL |

## 기술 후보 및 보류 조건

Java 21; Spring Boot **4.0.9 후보**, Cloud **2025.1.3 후보**. D1 공식 BOM/CI 검증 후 고정. Python 3.12/FastAPI, Node 22/React19/Vite7/Tailwind4, PostgreSQL16/pgvector **0.8.2 목표**. RDS 인스턴스의 실제 확장 버전 설치 검증 필수. 이 패키지는 빌드 실행 검증을 완료하지 않았습니다.

## 로컬 시작 (코드 골격)

1. `.env.example`을 `.env`로 복사한 뒤 비밀값 입력 (절대 커밋 금지).
2. `docker compose -f compose.local.yml config`로 구성 검사.
3. 프론트: `cd frontend && npm install && npm run dev`.
4. AI: `cd ai-service && python -m venv .venv && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8085`.
5. Java 서비스는 각 디렉터리 Gradle Wrapper 생성·의존성 검증 후 실행. `gradle wrapper` 및 `./gradlew bootRun` (Gradle 8.14.x 호환 여부 검증).
6. Compose는 DB/서비스별 이미지·JWT 등의 필수 구성까지 연동해야 전체 E2E가 완성됩니다. 초기 `compose.local.yml`은 데이터베이스 개발 지원용입니다.

## 디렉터리 책임

- `contracts/openapi/` Java REST API / error model 동결.
- `contracts/proto/` WorkOrder 조회 gRPC 단일 계약.
- `member-service/`, `asset-service/`, `workorder-service/`, `inventory-service/`, `gateway/` Java 서비스별 구성.
- `frontend/` 공통 라우팅·스타일·서비스별 features.
- `ai-service/`, `mcp-server/` Python AI 및 MCP adapter.
- `infra/` DB 확장·AWS·배포/비밀값 가이드.
- `scripts/` 로컬 브랜치 초기화. 실행 전 팀장 코드 리뷰 필수.

## Git 브랜치

`main` 안정, `develop` 통합, `docs` 기존 문서, `feature/{member,gateway,asset,workorder,inventory,ai,frontend-common,infra}` 개별, `release/v1.0` D15에 생성. 각 기능 PR의 대상은 `develop`.

이 ZIP을 로컬의 fixops 저장소 최상단으로 **파일 병합 복사**(기존 파일 덮어쓰기 금지)한 뒤 `feature/infra`에서 커밋하고 PR을 `develop`으로 보냅니다. 편의용 `scripts/bootstrap-branches.sh`는 `develop`을 원격 생성할 권한이 있는 사용자만 실행합니다.
