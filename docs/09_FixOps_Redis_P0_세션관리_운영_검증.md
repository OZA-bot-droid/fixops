# FixOps Redis P0 — 세션 상태 관리·운영·검증

> 구현 브랜치: `feature/redis-session-p0-latest` (main 병합 전). 이 문서는 Redis 도입 시점의 구현과 운영 제약을 기록하며 E2E 검증을 통과했다는 의미가 아니다.

## 1. 책임 분리

- Member PostgreSQL(`member_db`)의 `auth_sessions`가 로그인 세션의 영속 원본이다.
- `refresh_sessions.auth_session_id`는 회전되는 Refresh Token을 변경되지 않는 로그인 세션 UUID에 연결한다.
- Redis Key: `fixops:auth:active:{sid}`; Value: `memberId`; TTL: `auth_sessions.expires_at`까지.
- JWT Access Token에는 로그인 세션 `sid`가 포함된다. 기존 `iss`, `aud`, RS256 검증은 그대로 유지한다.
- Gateway의 WebFlux 인가는 Reactive Redis로 활성 세션을 확인한다. Member API는 자체 JWT 디코더에서 Redis 세션 유효성을 확인한다.
- Redis 키가 없거나 회원 ID 불일치·조회 오류가 발생하면 허용하지 않는다(fail closed). Redis 재시작 후 복구되지 않은 이전 세션은 사용자가 다시 로그인해야 할 수 있다.
- Redis 연결 서비스는 외부 포트를 게시하지 않으며 `REDIS_PASSWORD`는 추적되지 않는 .env에서 설정한다.

## 2. 세션 수명·흐름

1. 로그인: 비밀번호 확인 → DB `auth_sessions` + `refresh_sessions` 커밋 → Redis 활성 등록 → JWT/Refresh 응답. Redis 등록 실패 시 새 DB 세션 회수 보상 처리 및 응답 실패.
2. Refresh: 토큰 해시로 `sid` 조회 → `auth_sessions` 행 선점 잠금 → `refresh_sessions` 행 잠금·재검사 → 토큰 폐기/회전 → 동일 `sid` 유지. 유효성 실패 시 JWT 미발급.
3. 로그아웃: 인증된 JWT의 `sub`/`sid`와 Refresh Token의 세션 소유자 비교 → Redis 활성 키 삭제 → DB 세션 및 해당 Refresh Token 계열 폐기.
4. 전체 로그아웃: 회원 소유 활성 세션들을 잠그고 Redis 비활성화 및 DB 회수. JWT `sid`는 항상 검증한다.
5. 세션 최대 수명: 최초 로그인에서 7일(`fixops.jwt.refresh-days`)로 고정. Refresh 회전이 만료를 무기한 연장하지 않음.

## 3. 장애 및 현재 구현의 한계

- Redis는 PostgreSQL 트랜잭션에 참여하지 않는다. 로그인 보상/로그아웃의 Redis 선차단에도 중간 네트워크 실패, DB 장애, 다중 인스턴스 경합에 따른 불일치 가능성이 있다. 운영용 보강 시 outbox/reconciler, 상태 전이 관측 및 복구 설계가 필요하다.
- Redis AOF는 보조 내구성 설정이다. 키가 사라지면 안전하게 차단되지만 자동 복구/재인증 UX는 아직 별도 구현이 필요하다.
- 회전된 Refresh Token 재사용에 따른 **전체 세션 회수 탐지**는 기본 구현에 포함되지 않는다. 단순 재사용은 401이며, 도난/재사용 탐지 정책 강화가 후속 작업이다.
- 조직·단지·세대 접근은 JWT 세션 유효성과 별도다. 역할 변경·퇴거·관리권 이전 이벤트의 모든 세션 회수 연동과 각 업무 서비스 최신 권한 검증은 추가 구현이 필요하다.
- Redis 장애는 Gateway에서 인증 거부로 처리한다. 401/403 대신 503으로 응답하는 명시적 예외 분류는 추후 보완 대상이다.
- 직접 노출된 Member 외 업무 서비스의 JWT·조직 스코프 인가는 Gateway를 신뢰하는 것만으로 완성되지 않는다. 내부망 접근 통제 및 서비스별 보안 계약을 따로 검증해야 한다.
- 기존 `sid` 없는 JWT/Refresh Token은 새 구현과 호환되지 않는다. 개발/시연 환경 배포에서는 전원 재로그인을 안내한다.

## 4. 검증 명령

```powershell
# .env에 REDIS_PASSWORD 설정 (저장소에는 비밀값 커밋 금지)
docker compose config --quiet
docker compose up -d redis
docker compose ps redis
docker compose logs --tail 50 redis
docker compose up -d --build member gateway
cd member-service
./gradlew.bat clean test bootJar
cd ../gateway
./gradlew.bat clean test bootJar
```

## 5. E2E 체크리스트

| 시험 | 기대 결과 |
|---|---|
| 로그인 후 /api/members/me | 정상 JWT+Redis sid만 성공 |
| Refresh Token 회전 | 새 토큰 발급, 동일 sid |
| 이미 회전된 Refresh 재전송 | 401, 추가 토큰 미발급 |
| 명시적 로그아웃 | 기존 Access JWT로 /me 접근 차단 |
| 다른 회원 Refresh Token으로 로그아웃 | 소유자 불일치 거부 |
| 전체 로그아웃 | 같은 회원의 모든 sid 차단 |
| Redis Key 삭제 및 재시작 | 과거 세션 자동 허용 금지 |
| Redis 연결 차단 | 보호 API fail closed |
| 로그인 시 Redis 등록 실패 | 토큰 반환 없이 오류, DB 세션 보상 회수 |
| 동시 Refresh / 로그아웃 | 중복 발급·회수 세션 재활성화 없음 |
| 역할 변경·퇴거 | 최신 권한 강제/기존 토큰 회수 구현 후 별도 검증 |

> CI의 Java 컴파일·단위 테스트 통과는 Redis/PostgreSQL 실제 동시 동작 E2E 통과를 의미하지 않는다.
