# FixOps v1.0 — REST API·DTO·오류·서비스 간 계약

> **상태:** v1.0 설계 승인 후보 — 사용자 승인 정책 24개 및 권장 추가 쟁점 6개 반영. 실행·통합 테스트 전이며 코드 반영을 의미하지 않음.  
> **기준:** 2026-11-18 / 최종 발표 2026-12-21 / 5명·20영업일 / `OZA-bot-droid/fixops` main/docs v0.5~v0.5.4  
> **우선순위:** P0=시연 핵심 구현, P1=여력 시, P2=후속 확장.

## 1. 공통 규칙

- 외부 API prefix `/api`, 인증은 Bearer JWT, 내부 서비스는 호출자 신원과 사용자 위임 검증.
- 성공: `200/201/204`, 실패: 진짜 `4xx/5xx` (200에 `success:false` 넣지 않음).
- 시간은 ISO8601 UTC Z, 서버 저장 Java `Instant`/Postgres `TIMESTAMPTZ`.
- 커서/페이지 목록: `page`, `size`, `sort` 규격 통일 (size 상한 100), 전체 출력 제한.
- 생성/정정 중복 방지에 `Idempotency-Key` 헤더 사용; `organization_id + actor_id + operation + key` 조합으로 단일성 확인.
- 외부 값의 `orgId`, `complexId`, `memberId`를 요청 경로·본문으로 받더라도 권한 DB와 다시 대조.
- 권한이 없는 ID의 존재가 드러나면 안 되는 경우 404로 응답 가능; 정책을 서비스별로 일관되게 적용.

### 응답 예시

```json
{"success":true,"data":{"id":1001,"status":"ACTIVE"},"timestamp":"2026-11-18T01:00:00Z"}
```

```json
{"success":false,"error":{"code":"ORG_ACCESS_DENIED","message":"접근할 수 없습니다."},"traceId":"trace-id-example","timestamp":"2026-11-18T01:00:00Z"}
```

## 2. 필수 API 목록

| Method | Route | 대상 / 핵심 권한 | 상태 |
|---|---|---|---|
| POST | `/api/members/signup` | 공개(역할 입력 무시/거부) | P0 |
| POST | `/api/members/login` | 계정 인증 | P0 |
| POST | `/api/members/refresh` | 세션 검증·회전 | P0 |
| POST | `/api/organizations` | 인증 계정의 조직 개설 신청 | P0 |
| GET | `/api/platform/organizations/pending` | PLATFORM_ADMIN | P0 |
| PATCH | `/api/platform/organizations/{id}/decision` | PLATFORM_ADMIN 승인/거절 | P0 |
| GET | `/api/complexes/search?regionCode=...&q=...` | K-apt 후보, 서버 키 보유 | P0 |
| POST | `/api/organizations/{id}/complexes` | 조직 ADMIN 또는 조직신청 흐름, 중복 검증 | P0 |
| POST | `/api/organizations/{id}/join-requests` | 인증 사용자 | P0 |
| POST | `/api/organizations/{id}/invitations` | ADMIN | P0 |
| POST | `/api/invitations/accept` | 초대된 계정 | P0 |
| PATCH | `/api/organizations/{id}/members/{memberId}/role` | ADMIN, 마지막 ADMIN 보호 | P0 |
| POST | `/api/complexes/{complexId}/roster-imports` | 해당 단지 ADMIN, XLSX | P0 |
| GET | `/api/complexes/{complexId}/roster-imports/{jobId}` | 해당 단지 ADMIN | P0 |
| POST | `/api/complexes/{complexId}/roster-imports/{jobId}/commit` | 해당 단지 ADMIN, 멱등 | P0 |
| POST | `/api/resident-enrollments` | USER 본인 세대 연결 신청 | P0 |
| POST | `/api/resident-enrollments/{id}/verify` | 인증 및 명부 대조 | P0 |
| PATCH | `/api/resident-enrollments/{id}/decision` | 해당 단지 ADMIN | P0 |
| GET/POST | `/api/locations` | Asset 소유, org/complex 검증 | P0 |
| GET/POST | `/api/assets` | Asset 소유 | P0 |
| POST | `/api/work-orders` | 신고 권한 및 위치 확인 | P0 |
| PATCH | `/api/work-orders/{id}/assignment` | 해당 단지 ADMIN | P0 |
| PATCH | `/api/work-orders/{id}/status` | 역할·배정·상태 전이 검증 | P0 |
| GET | `/api/schedules/available` | 가용 슬롯 조회(REST fallback) | P0 |
| POST | `/api/schedules` | 사용자 확정 + Idempotency-Key | P0 |
| POST | `/api/schedules/{id}/cancel` | 해당 예약 권한·상태 | P0 |
| GET/POST | `/api/inventory/parts` | Inventory 범위 권한 | P0 |
| POST | `/api/inventory/receipts` | ADMIN 입고 | P0 |
| POST | `/api/inventory/usages` | 배정 작업·멱등성 검증 | P0 |
| POST | `/api/ai/chat` | 권한·RAG 문서 스코프 확인 | P0 |
| POST | `/api/ai/documents` | 문서 업로드 권한 | P0 |

`GET/POST`처럼 합쳐진 표기는 두 메서드의 **서로 다른 계약**을 뜻하며 실제 OpenAPI에서 분리한다.

## 3. DTO 예시

```json
{"organizationId":12,"complexId":44,"locationId":1203,"title":"수도 누수","description":"욕실 천장 누수"}
```

작업 생성 시 서버는 `reportedBy`를 JWT에서 얻고 조직/단지/세대 위치를 재검증한다.

```json
{"workOrderId":811,"technicianId":32,"startAt":"2026-12-18T04:00:00Z","endAt":"2026-12-18T05:00:00Z"}
```

예약 생성 시 `Idempotency-Key` 필수. 작업자·예약 시간 충돌은 WorkOrder DB에서 제약으로 재검증.

```json
{"workOrderId":811,"partId":92,"quantity":2,"operationKey":"usage-811-92-a"}
```

재고 사용은 Inventory가 작업 상태·조직·배정 관계 검증 후 단일 재고 트랜잭션 수행.

## 4. 오류 코드 매핑

| HTTP | 코드 | 의미 / 처리 |
|---|---|---|
| 400 | INVALID_INPUT | 유효하지 않은 입력 |
| 401 | UNAUTHENTICATED | 로그인·JWT 부재/만료 |
| 403 | ORG_ACCESS_DENIED | 조직 또는 단지·세대·작업 권한 없음 |
| 404 | RESOURCE_NOT_FOUND | 리소스 미존재 또는 비공개 정책 |
| 409 | COMPLEX_ALREADY_REGISTERED | K-apt 중복 단지 |
| 409 | ROSTER_IMPORT_CONFLICT | 동시 갱신/중복 커밋 |
| 409 | BOOKING_CONFLICT | 작업자 겹침·작업당 활성 예약 중복 |
| 409 | INVALID_STATE_TRANSITION | 상태 전이 불허 |
| 409 | INSUFFICIENT_STOCK | 차감 가능한 재고 부족(업무 충돌) |
| 409 | IDEMPOTENCY_KEY_REUSE | 동일 키로 다른 요청 본문 재사용 |
| 422 | ROSTER_VALIDATION_FAILED | 명부 행 검증 실패 |
| 429 | RATE_LIMITED | OTP·가입·조회 과다 |
| 503 | DEPENDENCY_UNAVAILABLE | 외부 K-apt/내부 DB·gRPC 의존성 장애 |
| 503 | AI_PROVIDER_UNAVAILABLE | LLM 응답 불가 |

## 5. 참조 데이터·상태 정책

- Asset의 위치/설비 삭제는 다른 서비스의 이력 참조가 있으면 `INACTIVE` 처리.
- WorkOrder가 참조하는 `locationId`/`assetId`는 조직·단지 소속 검증 후 저장.
- WORK_ORDER: `REPORTED→ASSIGNED→IN_PROGRESS→COMPLETED`, `REJECTED/CANCELLED` 종료; 재작업은 새 ID.
- MAINTENANCE_SCHEDULE: `CONFIRMED→CANCELLED/COMPLETED`. 사용자에게 표시된 빈 슬롯은 확정 보장 아님.
- Inventory는 사용 확정 전 WorkOrder 상태와 TECHNICIAN 권한 확인. 외부 API 장애 시 무조건 성공 처리 금지.

## 6. 통신 시간 제한·재시도

| 통신 | 초기 제한 | 재시도 |
|---|---|---|
| Gateway→Java REST | 총 3~5초 제안, API별 조정 | 조회만 제한 |
| AI→MCP | 5초 제안, LLM 별도 상한 | 조회 Tool 제한 |
| MCP→WorkOrder gRPC | Deadline 2초 제안 | UNAVAILABLE 등 조회만 |
| MCP→REST 대체 | 동일 사용자·서비스 인증 재검증 | 인증 실패 시 절대 대체 금지 |
| 예약 확정·재고 사용 | 멱등 키 + DB 제약 | 동일 키로 제한적 재시도 |

- 처리 시간이 긴 LLM 응답은 별도 서버 타임아웃·추후 SSE 정책으로 분리.
- 타임아웃 시간은 실측 후 동결할 **초기 제안값**.

## 7. 구현 전 계약 검증

- 각 서비스 OpenAPI 스펙 문서와 예시 요청/응답 작성, Mock Server로 상호 점검.
- Contract CI: API 성공·오류 스키마, JWT 권한 오류, 날짜 포맷, `Idempotency-Key`, 페이징.
- 변경 시 해당 서비스 담당자·팀장 리뷰 후 스키마 버전/호환성 확인.
