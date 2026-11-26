# FixOps — HttpOnly Cookie 잔여 개선 및 통합 검증 (최소 안정화안)

> 기준: 2026-11-25, `OZA-bot-droid/fixops`, `feature/member-front-auth@3b7d7eac3e6edc76ea83b119d7c617e3b7ddcaa9`. 이전 기준 `9158c3c` 이후 인증 파일 3개가 변경됨. 본 문서의 '교체안 완료'는 파일 작성 및 제한적 검증이며 실제 GitHub 반영/통합 테스트 완료가 아니다.

## 1. 범위 결정

- 유지: JWT RS256, Access Token 메모리, HttpOnly Refresh Cookie, Redis 활성 SID, PostgreSQL 세션/토큰 해시, Rotation, Origin/CSRF, 기존 권한/업무 화면.
- 완료 대상: SID/회원 검증, 전역 Gate, Web Locks/BroadcastChannel, 중복 요청 방지, 로그아웃 실패의 안전한 재로그인, 회전 토큰 재사용 탐지, JWT 만료 상한, 401/403/503 오류 구분.
- P2 제외: F5 이후 자동 로그아웃 재요청·복구, 멱등 요청 ID/결과 보존, Refresh 응답 유실 자동 복구, Redis/DB outbox/reconciler, 역할 변경에 따른 회수 연동. 프로젝트 핵심 서비스 통합 우선.
- Storage에는 재로그인 요구 표시(`fixops_reauth_required`)만 저장한다. 기존 `fixops_logout_pending`은 자동 복구하지 않고 마이그레이션 후 제거한다. 인증 토큰, 회원 ID, SID 원문을 Storage에 저장하지 않는다.
- 로그아웃 실패는 현재 페이지에서 재시도하거나 종료 미확인 상태로 재로그인. 새로고침 후에는 자동 인증 복원을 차단하고 재로그인 안내. 서버 로그아웃 성공으로 표시하지 않는다.

## 2. 최신 소스 재점검

| 항목 | 3b7d7ea 상태 | 교체안 |
|---|---|---|
| TokenResult 생성자/SID 응답 | 수정 완료 | 유지 |
| Refresh 회원/SID 프론트 검사 | 적용 | 서버 회전 전 동일성 검사 추가 |
| 전역 인증 Gate | 부분 적용; Gate 끝의 `user`, `platform` 미정의 참조 | 권한 검사를 Protected에만 유지 |
| 업무 API 상태/세대 검사 | 일부 적용 | 자동 재시도에도 최초 세대 유지 |
| Login/Refresh/Logout Web Locks | 적용 | 유지·단순화, 중첩 Lock 없음 |
| BroadcastChannel | 수신 함수 내부에서 리스너 등록 | 모듈 시작 시 1회 등록 |
| 정상 Logout 후 pending 제거 | 누락 | 기존 pending 제거 및 단순 재로그인 표식 |
| F5 이후 복구 | 불완전; `recoverLogout` 미연결 | 자동 복구 삭제, 안전한 재로그인 |
| Logout-all Cookie 소유권 | Bearer만 사용 | JWT sub/sid와 Cookie 일치 필수 |
| Refresh 재사용 탐지 | 기존 폐기 토큰 거부만 | 해당 활성 SID 전체 회수 후 401 |
| DB 조회 동시성 | 잠금 전 토큰 엔티티 조회 | SID 스칼라 조회, 이후 행 잠금 |
| JWT 만료 상한 | 미반영 | 세션 잔여 시간 이하 |
| Gateway Redis 오류 분류 | 오류와 비활성 모두 403 | 비활성 401, Redis 접근 장애 503 |
| Member CSRF 오류 JSON | traceId/timestamp 누락 | 공통 응답 필드 보완 |
| AuthProvider Context | 기존 속성 정상 | 변경 없음 |

## 3. 파일 적용 순서

1. 프론트/백엔드 파일을 함께 교체한다. Refresh 요청 body 및 logout-all 계약 변경이 연결된다. DB 스키마/Redis Key/기본 환경변수 변경 없음.
2. `docs/04` v1.1은 승인 후 공식 기준으로 교체한다. 기존 v1.0은 Git 이력 또는 별도 보관하고 현재 공식 문서 목록에서 제외한다. `contracts/error-codes.md`는 사용하지 않는다.
3. 09/10/README 반영. 03의 권한 정책은 유지하되 인증 전달·세션 정책 참조는 04/09에 둔다.
4. 독립 개발 환경에서 빌드·테스트 후 develop 병합. dev.py의 db-up/down 범위 개선과 운영 Compose 별도 override는 이번 인증 안정화 코드 범위 밖이다.

## 4. 새 API 계약

- 최초 복원: POST `/api/members/refresh` (body 없음 또는 null).
- 이미 인증된 탭의 갱신: `{"sessionId":"<UUID>","memberId":1}`. 서버는 Cookie의 실제 회원/SID와 일치 검사 후 회전한다. 불일치 403 `SESSION_SCOPE_DENIED`.
- logout 및 logout-all은 Bearer+같은 SID의 Cookie가 필요하다. 다른 세션 Cookie 삭제 금지.
- 세션 재사용 탐지: 활성·미만료 세션의 회전된 미만료 토큰을 재제출하면 해당 SID만 회수, 401 `REFRESH_TOKEN_REUSED`. 네트워크 응답 유실도 동일한 결과가 될 수 있으므로 재로그인.
- Gateway/Member: JWT 부재·검증 실패 401 `UNAUTHENTICATED`; SID 비활성 401 `SESSION_NOT_ACTIVE`; Redis 접근 장애 503 `SESSION_STORE_UNAVAILABLE`; 업무 권한 오류 403 유지.

## 5. 검증 상태 (파일 제작 환경)

- JavaScript 문법 검사: 통과 (`node --check`).
- JSX TypeScript transpile 문법 검사: 통과.
- Node 모의 브라우저/서버 테스트: 9/9 통과. 실제 Axios, 브라우저 Cookie, Redis, PostgreSQL, Spring Gateway를 실행한 결과는 아니다.
- Java 21 JavacTask 구문 파싱: 12개 Java 소스(신규 테스트 포함) 통과. 의존성/타입 확인 및 Gradle 컴파일은 미수행.
- Gradle/JUnit, npm ci/build, Docker, 실제 브라우저 통합 검증: 미수행. 본 자료는 완성된 교체용 소스이며 운영 검증 완료 보고서가 아니다.

## 6. 통합 검증 명령 (Windows PowerShell)

```powershell
# 저장소 루트 (개별 개발 환경)
node --experimental-vm-modules --test tests/auth-session.test.mjs
cd member-service
.\gradlew.bat clean test bootJar
cd ../gateway
.\gradlew.bat clean test bootJar
cd ../frontend
npm ci
npm run build
cd ..
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Docker `http://localhost:8088`. Vite 단독 환경 시험은 별도 호스트 `http://127.0.0.1:5173` 또는 반대로 분리한다. 실제 .env allowlist에 사용하는 Origin을 추가한다. `AUTH_COOKIE_SECURE=false`는 HTTP 로컬에만 적용하고 HTTPS 운영은 true. 팀 공용 DB/Redis 임의 종료 금지.

```powershell
# 로그아웃/쿠키 시험은 개인 테스트 계정·브라우저만 사용
curl.exe -i -X POST http://localhost:8088/api/members/clear-cookie
curl.exe -i -X POST http://localhost:8088/api/members/clear-cookie -H "Origin: http://localhost:8088" -H "X-FixOps-CSRF: 1"
```

첫 요청 403 CSRF_REJECTED(표준 JSON), 두 번째 200+삭제 Set-Cookie 기대. Cookie 삭제 API 자체는 서버 세션 회수를 보장하지 않는다.

## 7. 필수 E2E 수용 기준

| 시험 | 수용 기준 |
|---|---|
| 로그인/새로고침 | JSON refreshToken 없음; Cookie HttpOnly/Lax/Path/Max-Age; 동일 SID /me 정상 |
| 다중 탭 | 동일 Origin 두 탭에서 동시 F5/갱신; 정상 직렬화, 같은 SID |
| 계정 변경 | A 탭의 stale 요청이 B 계정의 권한으로 처리되지 않음; B Cookie 회전/삭제 방지 |
| 정상 로그아웃 | Cookie 삭제, Redis Key 제거, DB revoked, 기존 JWT 401, 다른 탭 초기화 |
| 전체 로그아웃 | 같은 회원 모든 SID 차단, 다른 회원 SID 유지 |
| 응답 차단 | logoutFailed에서 업무 API 차단, 동일 페이지 재시도 가능 |
| 실패 후 F5 | 자동 Refresh 없음, 명시적 재로그인 및 서버 종료 미확인 안내 |
| 응답 유실 | 이전 토큰 무조건 재허용 금지; 재로그인 |
| 재사용 탐지 | 회전된 미만료 토큰 재사용 → 401 및 해당 SID DB/Redis 회수, 다른 SID 유지 |
| Redis 키 없음 | 401 SESSION_NOT_ACTIVE |
| Redis 접근 장애 | 503 SESSION_STORE_UNAVAILABLE, 토큰 반환·업무 전달 금지 |
| CSRF | Origin 누락/불일치 및 헤더 누락 각각 403, 정상 요청 통과 |
| 업무 권한 | 조직 권한 부족 403 유지; 자동 Refresh하지 않음 |
| 환경 | 5173/8088 개별 테스트, HTTPS Secure, Redis 외부 미노출 |

테스트 토큰 원문은 문서/로그/스크린샷에 노출하지 않는다. Redis 중단 및 세션 강제 삭제는 개인 테스트 환경에서만 수행한다.

## 8. P2 한계 및 병합 승인

- Storage 사용 불가 환경은 F5 이후 표시 보존 불가. BroadcastChannel/Web Locks 미지원 또는 서로 다른 Origin은 탭 동기화 보장 없음. 서버 SID 검증은 유지된다.
- 로그아웃 응답 유실 시 이미 서버가 종료했을 수 있다. 재로그인 정책은 이전 세션 종료를 보장하지 않는다. 실패한 세션의 자동 복구/정확한 종료 결과 확인은 P2.
- Redis/DB 분산 트랜잭션은 여전히 아니다. Redis 선회수 후 DB 실패, 전체 로그아웃 중 부분 실패, 동시 신규 로그인 등 운영상 추가 검증/후속 설계 필요.
- Refresh Token 재사용 탐지의 엄격한 회수는 정상적인 경합 또는 응답 유실에도 재로그인을 유발할 수 있다. 이는 보수적인 정책 선택이며 팀 승인 필요.
- [ ] Gradle/JUnit 및 npm build 성공
- [ ] Docker/Vite 브라우저 E2E 완료
- [ ] 04 v1.1 오류 계약 승인
- [ ] HTTPS/Redis 배포 정책 승인
- [ ] P2 제외 범위 팀 합의

참고: [기준 커밋](https://github.com/OZA-bot-droid/fixops/commit/3b7d7eac3e6edc76ea83b119d7c617e3b7ddcaa9), [이전 대비 변경](https://github.com/OZA-bot-droid/fixops/compare/9158c3cb5c64b96bf9cd621009fc88de83e5341a...3b7d7eac3e6edc76ea83b119d7c617e3b7ddcaa9).
