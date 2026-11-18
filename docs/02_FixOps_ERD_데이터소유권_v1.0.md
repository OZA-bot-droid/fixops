# FixOps v1.0 — 논리 ERD·DB 소유권·데이터 수명주기

> **상태:** v1.0 설계 승인 후보 — 사용자 승인 정책 24개 및 권장 추가 쟁점 6개 반영. 실행·통합 테스트 전이며 코드 반영을 의미하지 않음.  
> **기준:** 2026-11-18 / 최종 발표 2026-12-21 / 5명·20영업일 / `OZA-bot-droid/fixops` main/docs v0.5~v0.5.4  
> **우선순위:** P0=시연 핵심 구현, P1=여력 시, P2=후속 확장.

## 1. 범위·키 계약

- 서비스별 DB: `member_db`, `asset_db`, `workorder_db`, `inventory_db`, `ai_db`.
- 각 서비스 내 물리 FK 가능; **서로 다른 DB 간 FK 금지**. 외부 ID는 명시적 논리 참조 + API 유효성 확인.
- `organization_id`, `complex_id`, `location_id`, `member_id`, `work_order_id`는 **BIGINT**를 기준으로 공유 계약. AI 문서/청크 UUID 별도 사용.
- 모든 다중 테넌트 업무 테이블의 `organization_id` 또는 `complex_id`(필요 시 둘 다)를 강제, 권한 SQL 필터 적용.
- 단지 식별(`complex_id`)은 소유 조직이 변경되어도 보존되는 **물리 자원 ID**; `organization_id`는 운영 책임의 시점별 관계. 변경 이력을 일괄 덮어쓰지 않음.

## 2. Member DB ERD

```mermaid
erDiagram
 MEMBER ||--o{ ORGANIZATION_MEMBER : belongs
 ORGANIZATION ||--o{ ORGANIZATION_MEMBER : contains
 ORGANIZATION ||--o{ COMPLEX_MANAGEMENT : manages
 APARTMENT_COMPLEX ||--o{ COMPLEX_MANAGEMENT : history
 ORGANIZATION_MEMBER ||--o{ MEMBER_COMPLEX_ACCESS : scoped
 APARTMENT_COMPLEX ||--o{ MEMBER_COMPLEX_ACCESS : access
 ORGANIZATION_MEMBER ||--o{ RESIDENT_UNIT : resides
 APARTMENT_COMPLEX ||--o{ RESIDENT_ROSTER : roster
 RESIDENT_ROSTER ||--o{ ROSTER_ENTRY : entries
 ROSTER_IMPORT_JOB ||--o{ ROSTER_STAGING_ROW : stages
 ROSTER_ENTRY ||--o{ RESIDENT_UNIT : matches
 ORGANIZATION ||--o{ JOIN_REQUEST : accepts
 ORGANIZATION ||--o{ INVITATION : invites
 MEMBER ||--o{ REFRESH_SESSION : sessions
 MEMBER {
   bigint id PK
   varchar email UK
   varchar password_hash
   varchar status
   boolean is_platform_admin
 }
 ORGANIZATION {
   bigint id PK
   varchar name
   varchar industry_type
   varchar status
 }
 ORGANIZATION_MEMBER {
   bigint id PK
   bigint organization_id FK
   bigint member_id FK
   varchar role
   varchar status
 }
 APARTMENT_COMPLEX {
   bigint id PK
   varchar kapt_code UK
   varchar display_name
   varchar road_address
   varchar verification_status
 }
 COMPLEX_MANAGEMENT {
   bigint id PK
   bigint complex_id FK
   bigint organization_id FK
   timestamptz started_at
   timestamptz ended_at
 }
 MEMBER_COMPLEX_ACCESS {
   bigint id PK
   bigint organization_member_id FK
   bigint complex_id FK
   varchar access_scope
   varchar status
 }
 RESIDENT_UNIT {
   bigint id PK
   bigint organization_member_id FK
   bigint roster_entry_id FK
   bigint location_id
   varchar status
 }
 RESIDENT_ROSTER {
   bigint id PK
   bigint complex_id FK
   varchar status
 }
 ROSTER_ENTRY {
   bigint id PK
   bigint roster_id FK
   bigint location_id
   varchar name
   varchar contact_ciphertext
   varchar contact_lookup_hmac
   varchar status
 }
 ROSTER_IMPORT_JOB {
   bigint id PK
   bigint organization_id FK
   varchar mode
   varchar status
   varchar file_sha256
   int row_count
 }
 ROSTER_STAGING_ROW {
   bigint id PK
   bigint import_job_id FK
   int row_no
   varchar validation_status
 }
 JOIN_REQUEST {
   bigint id PK
   bigint organization_id FK
   bigint member_id FK
   varchar status
 }
 INVITATION {
   bigint id PK
   bigint organization_id FK
   varchar email
   varchar token_hash
   timestamptz expires_at
 }
 REFRESH_SESSION {
   bigint id PK
   bigint member_id FK
   varchar token_hash
   timestamptz expires_at
 }
```

- `is_platform_admin`은 예시 표현이며 운영 구현에서는 별도 전역 역할·감사 엔티티로 확장 가능. 공개 API로 변경 불가.
- `organization_member(organization_id,member_id)` UNIQUE.
- `apartment_complex.kapt_code` UNIQUE NULL 허용; 수동 등록은 주소 유사 후보 + 플랫폼 관리자 검토.
- `complex_management` 활성 레코드(ended_at IS NULL)는 동일 complex당 **최대 하나**: partial unique index.
- `resident_unit`에 `location_id`는 Asset DB 논리 참조. 조직·단지·`location_type=UNIT` 재확인.
- `roster_staging_row(import_job_id,row_no)` UNIQUE; `commit` 단일 트랜잭션·중복 방지.
- `roster_entry`의 중복 판별은 **동/호수만으로 유일하지 않음**. 명부 소유 주민별 안정적인 key가 없는 경우 관리자 확인 및 연락처 HMAC 등을 보조로 활용.
- 물리 DB 스키마/DDL은 담당자별 Flyway V1/V2에서 확정, 여기에 없는 자동 FK 가정 금지.

## 3. Asset DB

```mermaid
erDiagram
 LOCATION ||--o{ LOCATION : parent
 LOCATION ||--o{ ASSET : contains
 ASSET_CATEGORY ||--o{ ASSET : classifies
 LOCATION {
   bigint id PK
   bigint organization_id
   bigint complex_id
   bigint parent_id FK
   varchar location_type
   varchar code
   varchar name
   varchar status
 }
 ASSET_CATEGORY {
   bigint id PK
   varchar industry_type
   varchar name
 }
 ASSET {
   bigint id PK
   bigint organization_id
   bigint complex_id
   bigint location_id FK
   bigint category_id FK
   varchar asset_code
   varchar status
 }
```

- BUILDING `COMPLEX→BUILDING→UNIT`, 공용 설비는 `COMMON_AREA` 위치 아래도 가능.
- FACTORY `FACTORY→AREA→LINE→EQUIPMENT_LOCATION` 등 별도 유형.
- 관리권 이전 시 `complex_id` 기준 자원 보존. 현재 운영 조직은 Member API를 통해 확인; `organization_id`의 히스토리 의미와 가변 운영자 의미를 혼합하지 않도록 별도 전략 필요.
- 이미 작업·예약 이력 있는 Location/Asset 삭제는 `INACTIVE`로 처리.

## 4. WorkOrder DB

```mermaid
erDiagram
 WORK_ORDER ||--o{ WORK_ORDER_HISTORY : changes
 WORK_ORDER ||--o{ MAINTENANCE_SCHEDULE : visits
 WORK_ORDER ||--o{ BOOKING_IDEMPOTENCY : deduplicates
 WORK_ORDER {
   bigint id PK
   bigint organization_id
   bigint complex_id
   bigint location_id
   bigint asset_id
   bigint reported_by
   bigint assigned_to
   varchar status
   bigint related_work_order_id
 }
 WORK_ORDER_HISTORY {
   bigint id PK
   bigint work_order_id FK
   varchar from_status
   varchar to_status
   bigint changed_by
   timestamptz created_at
 }
 MAINTENANCE_SCHEDULE {
   bigint id PK
   bigint work_order_id FK
   bigint technician_id
   timestamptz start_at
   timestamptz end_at
   varchar status
 }
 BOOKING_IDEMPOTENCY {
   bigint id PK
   bigint work_order_id FK
   varchar request_key
   varchar request_hash
 }
```

- `work_order.asset_id` nullable(누수 신고 등), `location_id`는 위치 범위 검증.
- DB EXCLUDE 제약으로 **작업자별 겹치는 활성 예약 금지**. 작업당 활성 확정 예약은 partial unique index 고려.
- `start_at < end_at` CHECK, idempotency key UNIQUE.

## 5. Inventory DB

```mermaid
erDiagram
 PART ||--|| STOCK_BALANCE : balance
 PART ||--o{ STOCK_MOVEMENT : movements
 PART ||--o{ PART_USAGE : used
 PART {
   bigint id PK
   bigint organization_id
   varchar sku
   varchar name
   varchar status
 }
 STOCK_BALANCE {
   bigint id PK
   bigint part_id FK
   int quantity
   bigint version
 }
 STOCK_MOVEMENT {
   bigint id PK
   bigint part_id FK
   int quantity_delta
   varchar movement_type
   varchar operation_key UK
   bigint work_order_id
 }
 PART_USAGE {
   bigint id PK
   bigint part_id FK
   bigint work_order_id
   int quantity
   varchar operation_key UK
 }
```

- 차감과 원장 삽입은 **한 Inventory 트랜잭션**, `quantity>=0` DB CHECK, 재시도 안전한 operation key.
- 타 서비스 WorkOrder ID는 FK 아님. 참조 작업의 조직·작업자 권한은 내부 API로 재검증.

## 6. AI DB

```mermaid
erDiagram
 KNOWLEDGE_DOCUMENT ||--o{ DOCUMENT_CHUNK : contains
 KNOWLEDGE_DOCUMENT {
   uuid id PK
   bigint organization_id
   bigint complex_id
   varchar visibility
   varchar status
   varchar storage_key
 }
 DOCUMENT_CHUNK {
   uuid id PK
   uuid document_id FK
   bigint organization_id
   vector embedding
   varchar content
 }
```

- RDS `ai_db`에 `vector` 0.8.2 사용, HNSW/코사인 검색. 조직·단지·문서 가시성 조건을 **SQL 검색 단계에서** 강제.
- 문서 삭제·권한 회수 시 검색 대상에서 즉시 제외하고 비동기 청크 정리를 수행.

## 7. 공통 데이터 삭제/이전 원칙

1. 물리 단지 식별자는 삭제되지 않으며, 운영 조직 변경시 `complex_management`만 닫고 새 활성 관리관계를 만든다.
2. 과거 작성/접수 시점의 조직 ID를 임의로 바꾸지 않는다. 신규 조직의 과거 자료 접근 정책은 운영자 승인·감사 하에서 별도 부여.
3. 완결 작업·예약·재고 원장은 정정 기록으로 보존.
4. 명부 원본 XLSX는 수집 목적에 필요한 최소 기간 후 S3 삭제; 휴대전화 원문은 암호화.
5. DB 간 참조 미존재/정지/조직 불일치 시 API는 403/404/409 중 계약에 따른 오류 반환.

## 8. DB 마이그레이션 책임

- Member/Asset/WorkOrder/Inventory 각각 Flyway. 한 RDS Postgres 16 인스턴스 내 논리 DB 개별 관리.
- AI DB는 Alembic. `vector` 확장 설치는 프로비저닝 단계에서 단 한 곳이 담당.
- 기존 vector 0.8.7의 다운그레이드를 `ALTER EXTENSION UPDATE`로 처리하지 말 것. 신규 DB 생성+재색인 경로를 사용.
