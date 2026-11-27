# FixOps Redis P0 — 세션 상태 관리·운영·검증

> 기준: `feature/member-front-auth@3b7d7ea` 및 인증 안정화 교체안. 적용·통합 검증 전. 오류 계약은 04 v1.1 승인본 참조.

## 1. 책임 분리

- Member PostgreSQL(`member_db`)의 `auth_sessions`가 로그인 세션의 영속 원본이다.
- `refresh_sessions.auth_session_id`는 회전되는 Refresh Token을 변경되지 않는 로그인 세션 UUID에 연결한다.
- Redis Key: `fixops:auth:active:{sid}`; Value: `memberId`; TTL: `auth_sessions.expires_at`까지.
- JWT Access Token에는 로그인 세션 `sid`가 포함된다. 기존 `iss`, `aud`, RS256 검증 유지. Access Token은 메모리, Refresh Token은 HttpOnly Cookie.
- Gateway의 WebFlux 인가는 Reactive Redis로 활성 세션을 확인한다. Member API는 JWT 서명/만료 검증 후 별도 `MemberSessionFilter`에서 Redis SID를 검증한다.
- Redis 키가 없거나 회원 ID 불일치·조회 오류가 발생하면 허용하지 않는다(fail closed). 키 없음 401, Redis 접근 오류 503으로 구분한다.
- 실제 Compose는 로컬 IDE 개발을 위해 Redis를 기본 `127.0.0.1`에만 게시한다. 운영 배포에서는 외부에 Redis 포트를 게시하지 않도록 별도 환경 설정한다. `REDIS_PASSWORD`는 추적되지 않는 .env에서 설정한다.

## 2. 세션 수명·흐름

1. 로그인: 비밀번호 확인 → DB `auth_sessions` + `refresh_sessions` 커밋 → Redis 활성 등록 → JWT/HttpOnly Refresh Cookie 응답. Redis 등록 실패 시 새 DB 세션 회수 보상 및 응답 실패.
2. Refresh: 토큰 해시로 SID만 조회 → `auth_sessions` 행 잠금 → `refresh_sessions` 행 잠금·재검사 → 기존 탭의 예상 회원/SID 일치 확인 → 토큰 회전 → 동일 SID 유지. 유효성 실패 시 JWT 미발급.
3. 로그아웃: JWT `sub`/`sid`와 Cookie 소유자 비교 → Redis 활성 키 삭제 → DB 세션 및 해당 Refresh Token 계열 폐기 → Cookie 삭제 응답.
4. 전체 로그아웃: 회원 소유 활성 세션들을 잠그고 JWT SID와 Cookie 소유권 일치 확인 → Redis 비활성화 및 DB 회수 → Cookie 삭제. 다른 회원 또는 다른 SID Cookie는 변경하지 않는다.
5. 세션 최대 수명: 최초 로그인에서 7일(`fixops.jwt.refresh-days`)로 고정. Refresh 회전이 만료를 무기한 연장하지 않는다. JWT 수명도 세션 잔여 수명을 초과하지 않는다.
6. 활성 세션의 만료 전 회전된 토큰 재사용: 해당 SID의 Redis와 DB를 회수하고 DB 트랜잭션 커밋 후 401 `REFRESH_TOKEN_REUSED`. 정상적인 동시 요청/응답 유실도 감지될 수 있으므로 재로그인한다.

## 3. 장애 및 현재 구현의 한계

- Redis는 PostgreSQL 트랜잭션에 참여하지 않는다. 로그인 보상/로그아웃의 Redis 선차단에도 중간 네트워크 실패, DB 장애, 다중 인스턴스 경합에 따른 불일치 가능성이 있다. 운영용 보강 시 outbox/reconciler, 상태 전이 관측 및 복구 설계가 필요하다.
- Redis AOF는 보조 내구성 설정이다. 키가 사라지면 안전하게 차단되며 사용자는 재로그인한다. 회수 중 DB 실패 후 Redis를 DB에서 자동 복구하는 정책은 도입하지 않는다.
- 조직·단지·세대 접근은 JWT 세션 유효성과 별도다. 역할 변경·퇴거·관리권 이전 이벤트의 모든 세션 회수 연동과 각 업무 서비스 최신 권한 검증은 추가 구현이 필요하다.
- 직접 노출된 Member 외 업무 서비스의 JWT·조직 스코프 인가는 Gateway를 신뢰하는 것만으로 완성되지 않는다. 내부망 접근 통제 및 서비스별 보안 계약을 따로 검증해야 한다.
- Gateway/Member의 일반 업무 권한 부족은 403으로 유지. Redis 장애는 해당 접근만 503으로 구분하며 DB 오류를 Redis 장애로 위장하지 않는다.
- 기존 `sid` 없는 JWT/Refresh Token은 새 구현과 호환되지 않는다. 개발/시연 환경 배포에서는 전원 재로그인을 안내한다.
- 브라우저 로그아웃 실패 시 업무 요청 차단. 같은 페이지에서는 재시도, 새로고침 후에는 자동 복구를 하지 않고 명시적 재로그인. 서버 세션 종료 미확인을 성공으로 표시하지 않는다. Storage가 차단된 환경에서는 F5 이후 차단 표시 유지가 보장되지 않는다.

## 4. 검증 명령

```powershell
# .env에 REDIS_PASSWORD 설정 (저장소에는 비밀값 커밋 금지)
docker compose config --quiet
docker compose up -d redis
docker compose ps redis
docker compose logs --tail 50 redis
docker compose up -d --build member gateway
cd member-service
.\gradlew.bat clean test bootJar
cd ../gateway
.\gradlew.bat clean test bootJar
```

전체 브라우저 시험은 프로젝트 루트에서 `docker compose up -d --build`로 수행한다. 팀 공용 Redis·DB의 중단/키 삭제 시험 금지. 개인 테스트 환경만 사용한다.

## 5. E2E 체크리스트

| 시험 | 기대 결과 |
|---|---|
| 로그인 후 /api/members/me | 정상 JWT+Redis SID만 성공 |
| Refresh Token 회전 | 새 쿠키, 동일 SID |
| 이미 회전된 만료 전 Refresh 재전송 | 401 REFRESH_TOKEN_REUSED, 해당 SID 회수, 다른 SID 유지 |
| 명시적 로그아웃 | 기존 Access JWT로 /me 접근 차단 |
| 다른 회원/다른 SID Cookie로 갱신·로그아웃 | 소유권 불일치 403, Cookie·세션 불변 |
| 전체 로그아웃 | 같은 회원의 모든 SID 차단 |
| Redis Key 없음 | 401 SESSION_NOT_ACTIVE |
| Redis 연결 차단 | 503 SESSION_STORE_UNAVAILABLE, 보호 API fail closed |
| 로그인 시 Redis 등록 실패 | 토큰 반환 없이 오류, DB 세션 보상 회수 |
| 동시 Refresh / 로그아웃 | Deadlock 없음, 회수 세션 재활성화 없음 |
| Redis 재시작 | 키가 유실된 세션 자동 허용 금지 |
| 역할 변경·퇴거 | 최신 권한 강제/기존 토큰 회수 구현 후 별도 검증 |

> CI의 Java 컴파일·단위 테스트 통과는 Redis/PostgreSQL 실제 동시 동작 E2E 통과를 의미하지 않는다.
