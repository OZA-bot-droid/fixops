# FixOps v1.1 — REST API·DTO·오류·서비스 간 계약

> 기준: 2026-11-25, `feature/member-front-auth@3b7d7eac3e6edc76ea83b119d7c617e3b7ddcaa9` 및 인증 안정화 교체안.
> 상태: 적용·팀 승인 후보. v1.0의 업무 API 및 권한 계약은 유지한다. 승인 후 이 문서 하나를 공식 오류 계약으로 사용하며 v1.0은 이력으로 보관한다. `contracts/error-codes.md`는 사용하지 않는다.
> P0=시연 핵심, P1=안정화, P2=후속 확장. 실제 통합 테스트 전이다.

## 1. 공통 규칙

- 외부 API prefix `/api`, 보호 API는 Bearer JWT와 활성 Redis SID를 검증한다. 내부 서비스는 호출자 신원과 사용자 위임 검증.
- 성공: `200/201/204`, 실패: 실제 `4xx/5xx` (200에 `success:false` 넣지 않음).
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
| POST | `/api/members/signup` | 공개(역할 입력 무시/거부), Origin/CSRF | P0 |
| POST | `/api/members/login` | 계정 인증, HttpOnly Cookie 발급, Origin/CSRF | P0 |
| POST | `/api/members/refresh` | Cookie 검증·회전, 예상 회원/SID 검증, Origin/CSRF | P0 |
| POST | `/api/members/logout` | Bearer+동일 SID Cookie, 해당 세션 회수 | P0 |
| POST | `/api/members/logout-all` | Bearer+동일 SID Cookie, 회원 전체 활성 세션 회수 | P1 |
| POST | `/api/members/clear-cookie` | Origin/CSRF, 로컬 Cookie 삭제만 수행 | P1 |
| GET | `/api/members/me` | 인증된 회원 | P0 |
| POST | `/api/organizations` | 인증 계정의 조직 개설 신청 | P0 |
| GET | `/api/platform/organizations/pending` | PLATFORM_ADMIN | P0 |
| PATCH | `/api/platform/organizations/{id}/decision` | PLATFORM_ADMIN 승인/거절 | P0 |
| GET | `/api/complexes/search?regionCode=...&q=...` | K-apt 후보, 서버 키 보유 | P0 |
| POST | `/api/organizations/{id}/complexes` | 조직 ADMIN 또는 조직신청 흐름, 중복 검증 | P0 |
| GET | `/api/platform/complex-managements/{id}/address-candidates` | PLATFORM_ADMIN, 주소 중복 후보 조회 | P0 |
| PATCH | `/api/platform/complex-managements/{id}/decision` | PLATFORM_ADMIN, 승인/거절 및 별개 단지 확인 | P0 |
| POST | `/api/organizations/{id}/join-requests` | 인증 사용자 | P0 |
| POST | `/api/organizations/{id}/invitations` | ADMIN | P0 |
| POST | `/api/invitations/accept` | 초대된 계정 | P0 |
| PATCH | `/api/organizations/{id}/members/{memberId}/role` | ADMIN, 마지막 ADMIN 보호 | P0 |
| POST | `/api/complexes/{complexId}/roster-imports` | 해당 단지 ADMIN, XLSX | P0 |
| GET | `/api/complexes/{complexId}/roster-imports/{jobId}` | 해당 단지 ADMIN | P0 |
| POST | `/api/complexes/{complexId}/roster-imports/{jobId}/commit` | 해당 단지 ADMIN, 멱등 | P0 |
| GET | `/api/resident-enrollments/verification-methods` | 인증 계정, 활성 인증 방식 조회 | P0 |
| GET | `/api/resident-enrollments/complexes` | 이메일 검증 계정, 등록 가능한 단지 검색 | P0 |
| POST | `/api/resident-enrollments/redeem-code` | 이메일 검증 계정, 일회용 등록 코드 인증 | P0 |
| GET | `/api/resident-enrollments/mine` | 본인 신청 목록 | P0 |
| GET | `/api/resident-enrollments/pending` | 해당 단지 ADMIN | P0 |
| GET | `/api/complexes/{complexId}/resident-registration-codes` | 해당 단지 ADMIN, 발급 대상 조회 | P0 |
| POST | `/api/complexes/{complexId}/resident-registration-codes` | 해당 단지 ADMIN, 발급·재발급 | P0 |
| DELETE | `/api/complexes/{complexId}/resident-registration-codes/{codeId}` | 해당 단지 ADMIN, 미사용 코드 회수 | P0 |
| POST | `/api/resident-enrollments` | 이메일 검증 계정, 선택적 SMS 신청 | P0 |
| POST | `/api/resident-enrollments/{id}/verify` | 인증 및 명부 대조 | P0 |
| PATCH | `/api/resident-enrollments/{id}/decision` | 해당 단지 ADMIN | P0 |
| GET/POST | `/api/locations` | Asset 소유, org/complex 검증 | P0 |
| GET/POST | `/api/assets` | Asset 소유 | P0 |
| POST | `/api/work-orders` | 신고 권한 및 위치 확인 | P0 |
| PATCH | `/api/work-orders/{id}/assignment` | 해당 단지 ADMIN, 배정 | P0 |
| PATCH | `/api/work-orders/{id}/status` | 역할·배정·상태 전이 검증 | P0 |
| GET | `/api/schedules/available` | 가용 슬롯 조회(REST fallback) | P0 |
| POST | `/api/schedules` | 사용자 확정 + Idempotency-Key | P0 |
| POST | `/api/schedules/{id}/cancel` | 해당 예약 권한·상태 | P0 |
| GET/POST | `/api/inventory/parts` | Inventory 범위 권한 | P0 |
| POST | `/api/inventory/receipts` | ADMIN 입고 | P0 |
| POST | `/api/inventory/usages` | 배정 작업·멱등성 검증 | P0 |
| POST | `/api/ai/chat` | 권한·RAG 문서 스코프 확인 | P0 |
| POST | `/api/ai/documents` | 문서 업로드 권한 | P0 |

`GET/POST`처럼 합쳐진 표기는 두 메서드의 서로 다른 계약을 뜻하며 실제 OpenAPI에서 분리한다.

### 2.1 인증 전달 및 Cookie

로그인·Refresh 응답 JSON은 Access Token만 포함한다. Refresh Token 원문은 JSON·Storage·로그에 노출하지 않는다. 예시:

```json
{"success":true,"data":{"accessToken":"<access-jwt>","tokenType":"Bearer","expiresIn":900,"sessionId":"11111111-2222-4333-8444-555555555555","member":{"id":1,"email":"member@example.com","name":"테스트","platformAdmin":false,"memberships":[]}},"timestamp":"2026-11-25T05:00:00Z"}
```

`sessionId`는 JWT `sid`와 같은 UUID이며 비밀 인증값이 아니다. `expiresIn`은 실제 JWT 수명(초)이며 인증 세션 잔여 수명을 초과하지 않는다.

| 속성 | 값 |
|---|---|
| Name | `fixops_refresh` |
| HttpOnly | true |
| Secure | 운영 HTTPS true, 로컬 HTTP false |
| SameSite | Lax |
| Path | `/api/members` |
| Domain | 생략(host-only) |
| Max-Age | 최초 로그인 기준 최대 7일 세션의 잔여 시간 |

- 인증 POST 6개 모두 정확한 Origin allowlist 및 `X-FixOps-CSRF: 1` 검사. 누락/불일치 403 `CSRF_REJECTED`.
- 동일 Origin `/api` 프록시; 인증 Axios `withCredentials:true` 및 CSRF 헤더 사용. 공개 로그인·갱신에 Bearer 자동 첨부 금지.
- 최초 인증 복원은 `/members/refresh`에 body 없음 또는 null. 기존 인증 갱신은 `{ "sessionId": "<UUID>", "memberId": 1 }`을 보낸다. 둘 중 하나만 지정하면 400. 서버가 Cookie에서 얻은 회원·SID와 일치하는지 회전 전에 확인하고 불일치 시 403, Cookie/DB 변경 금지. 이 값들은 인증 수단이 아니라 일치 제약이다.
- 로그아웃/전체 로그아웃은 둘 다 인증된 JWT의 `sub`/`sid`와 Cookie 소유자를 서버에서 비교한다. 성공할 때만 동일 Name/Path/Domain의 `Max-Age=0` Cookie를 반환한다.
- 회전된 토큰을 활성 세션에서 만료 전 다시 제출하면 해당 SID 전체 회수 후 401 `REFRESH_TOKEN_REUSED`. 다른 기기 SID는 유지. 응답 유실/비정상 동시 갱신도 재사용으로 감지될 수 있으므로 재로그인 정책 적용.
- `clear-cookie`는 서버 세션 회수 API가 아니다. 프론트의 다시 로그인 버튼은 이 API를 자동 호출하지 않으며 로컬 인증을 폐기하고 명시적 로그인으로 전환한다.
- Web Locks/BroadcastChannel은 동일 Origin 범위만 지원한다. Cookie는 포트로 분리되지 않는다. 독립 Vite/Compose 시험은 localhost와 127.0.0.1 등 호스트를 분리한다.
- 운영 HTTPS 필수, `AUTH_COOKIE_SECURE=true`, 실제 서비스 HTTPS Origin만 allowlist. 기존 브라우저 `fixops_demo_refresh` 등 과거 저장 토큰은 사용하지 않는다. 배포 시 저장값 정리 및 재로그인 안내.

### 2.2 주민 인증 방식 및 등록 코드 (2026-11-25 구현 개정)

- GET verification-methods: 200, data = { "registrationCodeEnabled": true, "smsEnabled": false }. SMS 값은 Provider 설정 여부이며 발송 성공 보장이 아니다.
- GET complexes: q, page=0, size=20 (최대 100). 활성 관리관계 및 명부가 있는 검증 단지를 조회한다.
- GET resident-registration-codes: page, size. data 배열 항목은 id, building_no, unit_no, name, linked, pending_review, code_id, expires_at. 연락처 및 코드 원문은 목록에 포함하지 않는다.
- POST resident-registration-codes: { "rosterEntryId": 123 } → 201, data = { "id": 456, "code": "<32자리 16진수>", "expiresInSeconds": 86400 }. 재발급 시 기존 미사용 코드를 회수한다. 원문은 발급 응답에서만 제공하며 자동 재시도 금지.
- DELETE resident-registration-codes/{codeId}: 204, 본문 없음.
- POST redeem-code: { "complexId": 44, "code": "<32자리 16진수>" } → 200, data = { "verified": true, "id": 789, "status": "PENDING_REVIEW", "verificationMethod": "REGISTRATION_CODE" }. 자동 연결 성공 시 APPROVED. 승인 후 세션 회수 및 재로그인 필요.
- POST 기존 SMS 신청: complexId, buildingNo, unitNo, contact → 201; POST /{id}/verify는 6자리 code, 200 또는 계약 오류. SMS는 Provider 활성화 시 발송 가능.
- GET mine 및 pending 목록에는 verification_method 포함. PATCH /{id}/decision의 본문은 { "approve": true }.
- 등록 코드·인증 방식 조회 응답은 Cache-Control: no-store. 코드 원문, OTP, 연락처를 로그에 남기지 않는다.
- 오류: 유효하지 않거나 만료/회수/사용된 코드 400 INVALID_INPUT; 범위 위반 또는 이메일 미검증 403 ORG_ACCESS_DENIED; 리소스 부재 404 RESOURCE_NOT_FOUND; 이미 연결/중복 심사/회수 불가 상태 409 INVALID_STATE_TRANSITION; 계정별 15분 실패 5회 이후 429 RATE_LIMITED; 인증 발송 또는 필수 의존성 장애 503 DEPENDENCY_UNAVAILABLE. 기존 오류 코드를 재사용하며 신규 오류 코드 파일을 만들지 않는다.
- 본 절은 fixopsdev 구현 브랜치의 Controller/Service 계약을 문서화한 것으로 실제 HTTP·DB 회귀 검증 및 팀 승인은 별도이다.

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

이 표는 확장 가능한 공식 계약이다. 신규 업무 오류는 기존 코드의 의미와 중복되지 않는 경우 추가하며, HTTP 상태·발생 조건·클라이언트 처리·서비스 소유자를 문서와 코드에 함께 반영한다. 기존 인증 코드는 하위 호환성을 유지한다. 오류 코드는 대문자 SNAKE_CASE, 메시지는 안전한 사용자용 문구로 제한하고 내부 예외·개인정보·인증정보를 노출하지 않는다.

| HTTP | 코드 | 의미 / 처리 |
|---|---|---|
| 400 | INVALID_INPUT | 유효하지 않은 입력, JSON/파라미터/DTO 검증 실패 |
| 400 | INVALID_ORGANIZATION_KIND | 지원하지 않는 조직 유형 (Member) |
| 400 | REJECTION_REASON_REQUIRED | 조직 거절 사유 누락 (Member) |
| 401 | UNAUTHENTICATED | 로그인·JWT 부재/만료/검증 실패 |
| 401 | INVALID_CREDENTIALS | 로그인 자격증명 오류 |
| 401 | REFRESH_COOKIE_MISSING | Refresh Cookie 없음 |
| 401 | INVALID_REFRESH_TOKEN | 유효하지 않거나 만료된 Refresh Token |
| 401 | INVALID_SESSION | 세션 불일치/부재/폐기 등 |
| 401 | SESSION_NOT_ACTIVE | 활성 Redis SID 없음 또는 회원 불일치 |
| 401 | SESSION_EXPIRED | 세션 잔여 수명 부족 |
| 401 | REFRESH_TOKEN_REUSED | 회전 토큰 재사용 탐지, 해당 SID 회수 |
| 403 | CSRF_REJECTED | Origin/CSRF 헤더 검증 실패 |
| 403 | SESSION_SCOPE_DENIED | 예상/인증 회원 또는 SID와 Cookie 불일치 |
| 403 | ORG_ACCESS_DENIED | 조직 또는 단지·세대·작업 권한 없음 |
| 403 | EMAIL_VERIFICATION_REQUIRED | 이메일 소유 검증 필요 (Member) |
| 403 | PLATFORM_ADMIN_REQUIRED | 플랫폼 관리자 권한 필요 (Member) |
| 404 | RESOURCE_NOT_FOUND | 리소스 미존재 또는 비공개 정책 |
| 409 | EMAIL_ALREADY_EXISTS | 이메일 중복 |
| 409 | COMPLEX_ALREADY_REGISTERED | 동일 단지의 활성 관리권 중복 (Member) |
| 409 | COMPLEX_ADDRESS_REVIEW_REQUIRED | 동일 주소의 다른 단지 후보 존재; 플랫폼 관리자의 별개 단지 확인 필요 (Member) |
| 409 | BUILDING_MANAGEMENT_VERIFICATION_REQUIRED | 검증된 단지 관리권 없이 BUILDING 조직 승인 불가 (Member) |
| 409 | ROSTER_IMPORT_CONFLICT | 명부 버전 충돌 또는 FULL 누락 확인 미완료; 완료 작업 재커밋은 멱등 성공 (Member) |
| 409 | BOOKING_CONFLICT | 작업자 겹침·작업당 활성 예약 중복 |
| 409 | INVALID_STATE_TRANSITION | 상태 전이 불허 |
| 409 | INSUFFICIENT_STOCK | 차감 가능한 재고 부족(업무 충돌) |
| 409 | IDEMPOTENCY_KEY_REUSE | 동일 키로 다른 요청 본문 재사용 |
| 413 | PAYLOAD_TOO_LARGE | Multipart 요청 크기 초과 (Member) |
| 422 | ROSTER_VALIDATION_FAILED | 명부 행 검증 실패 |
| 429 | RATE_LIMITED | 로그인·OTP·가입·K-apt 조회/등록 과다 |
| 500 | INTERNAL_ERROR | 예상하지 못한 서버 오류; 내부 상세 비공개 |
| 503 | SESSION_STORE_UNAVAILABLE | Redis 인증 조회/회수 장애; 보호 요청 차단 |
| 503 | DEPENDENCY_UNAVAILABLE | 외부 K-apt/내부 DB·gRPC 의존성 장애 |
| 503 | AI_PROVIDER_UNAVAILABLE | LLM 응답 불가 |

미분류 HTTP 오류의 호환 fallback은 `REQUEST_FAILED`이며 원래 HTTP 상태를 유지한다. Member의 401은 `WWW-Authenticate: Bearer`, 오류 응답은 `Cache-Control: no-store`를 적용한다. 429는 반복 자동 재시도를 금지하고 제한 해제 후 명시적 재요청한다. 413/422는 파일 및 입력 수정 안내, 409는 상태 재조회 또는 누락 검토를 안내한다. 500/503은 인증 만료로 처리하지 않는다.

프론트는 503을 세션 만료로 간주하지 않는다. 403 업무 권한 거부를 자동 Refresh로 처리하지 않는다. 잘못된 세션 오류가 확인되면 로컬 인증 폐기. `SESSION_REVOKED`는 기존 호환 처리 코드로 프론트에서 인식하지만 이번 교체안은 별도 발급하지 않는다. 위 표는 목표 계약과 이번 인증 코드의 주요 반환값을 명시한 것이며 모든 서비스의 오류 핸들러를 실제 실행 검증했다는 의미가 아니다.

### 4.1 단지 주소 충돌 심사

동일 주소는 중복 후보일 뿐 단지 동일성의 확정 근거가 아니다. 동일 K-apt 코드는 기존 물리 단지 ID를 재사용하며 활성 관리권 중복은 금지한다. 주소 후보가 존재하면 PLATFORM_ADMIN은 후보 조회 후 증빙을 검토하고 승인 요청에 `confirmDistinctAddress: true` 및 `reason`을 전달한다. 확인은 서버에서 승인 직전 재검증하고 V8의 `address_collision_confirmed`와 심사 이력을 저장한다. 확인값은 생략 시 false이며 자동 병합은 금지한다. 관리권 이전은 별도 계약이다.

## 5. 참조 데이터·상태 정책

- Asset의 위치/설비 삭제는 다른 서비스의 이력 참조가 있으면 `INACTIVE` 처리.
- WorkOrder가 참조하는 `locationId`/`assetId`는 조직/단지 소속 검증 후 저장.
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
- 타임아웃 시간은 실측 후 동결할 초기 제안값.
- 인증 갱신 자동 재시도는 기존 안전한 조회 API 원칙을 따른다. 로그아웃 실패는 동일 페이지에서 명시적 재시도, F5 이후 안전한 재로그인. 자동 복구·요청 ID 기반 멱등성은 P2.

## 7. 구현 전 계약 검증

- 각 서비스 OpenAPI 스펙 문서와 예시 요청/응답 작성, Mock Server로 상호 점검.
- Contract CI: API 성공·오류 스키마, JWT 권한 오류, 날짜 포맷, `Idempotency-Key`, 페이징.
- Cookie/Origin, 다중 탭, SID 검증, Redis 장애, 재사용 및 로그아웃 시험은 09/10 문서 참조.
- 변경 시 해당 서비스 담당자·팀장 리뷰 후 스키마 버전/호환성 확인.


### 조직 개설 초대 API (2026-11-26)
| Method | Path | 권한 |
|---|---|---|
| POST | /platform/organizations/onboarding-invitations | PLATFORM_ADMIN |
| GET | /platform/organizations/onboarding-invitations | PLATFORM_ADMIN, page/size |
| PATCH | /platform/organizations/onboarding-invitations/{id}/revoke | PLATFORM_ADMIN |
| GET | /organizations/onboarding-invitations/mine | 인증 이메일 회원 |
| POST | /organizations/onboarding-invitations/{id}/accept | 초대 대상 인증 이메일 회원 |

발급 body: email, name, kind. 응답 id, expiresInSeconds, emailSent. 수락 응답은 기존 Organization DTO. 오류는 기존 ORG_ACCESS_DENIED(403), INVALID_STATE_TRANSITION(409), INVALID_INPUT(400)을 재사용한다.
