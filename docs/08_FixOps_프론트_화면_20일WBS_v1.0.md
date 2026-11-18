# FixOps v1.0 — 화면 목록·업무분장·20영업일 WBS

> **상태:** v1.0 설계 승인 후보 — 사용자 승인 정책 24개 및 권장 추가 쟁점 6개 반영. 실행·통합 테스트 전이며 코드 반영을 의미하지 않음.  
> **기준:** 2026-11-18 / 최종 발표 2026-12-21 / 5명·20영업일 / `OZA-bot-droid/fixops` main/docs v0.5~v0.5.4  
> **우선순위:** P0=시연 핵심 구현, P1=여력 시, P2=후속 확장.

## 1. 화면 범위 P0

| 사용자 | 화면/모달 | 주 담당 |
|---|---|---|
| 공통 | 로그인, 회원가입, 계정 복원, 조직 검색·가입·승인 대기 | 오좌언 |
| PLATFORM_ADMIN | 조직 승인 목록·상세·승인/거절, 단지 중복 검토 | 오좌언 |
| ADMIN | 조직/단지 목록, 구성원 초대·승인·역할 변경 | 오좌언 |
| ADMIN | XLSX 명부 업로드·미리보기·오류·확정 | 이도형 UI / 오좌언 API |
| ADMIN | Location·Asset CRUD, 설비 정보 Modal | 이도형 |
| ADMIN | 신고 접수·배정·상태·예약·방문 관리 | 박천보 |
| ADMIN | 부품·입고·재고·이력 | 이도관 |
| TECHNICIAN | 내 작업·일정·사용 부품 입력 | 박천보·이도관 |
| USER | 단지·세대 연결, 신고 생성·이력, 예약 후보·확정 | 오좌언·박천보 |
| 권한별 | AI 상담/매뉴얼 출처, 허용 Tool 결과 | 하진철 |

- 공유 UI: Button, Input, Modal, ConfirmDialog, Table, Pagination, StatusBadge, Header, Sidebar, AppLayout.
- 전역 Tailwind CSS 4 `@theme`, SCSS·CSS Modules 미사용. 모달은 키보드·접근성·반응형 고려.
- React Router UI 가드는 서버 JWT/RBAC/Complex ACL을 대체하지 않는다.

## 2. 5인 업무분장 확정

| 구분 | 담당 | 백엔드/도메인 | Python AI 기여 | 배포·검증 |
|---|---|---|---|---|
| 팀장 | **오좌언** | Member, Org, JWT, Gateway, K-apt, 명부 Import API | AI 보안·위임 사용자 컨텍스트 | AWS/CI/공통 API·Trace |
| A | **이도형** | Asset, Location, 단지·동·호수, 명부 Import UI | Asset MCP Tool | Asset FK/조직 격리 |
| B | **박천보** | WorkOrder, 예약·충돌, gRPC Server | WorkOrder/Booking MCP Tool | Proto/동시 예약 테스트 |
| C | **이도관** | Inventory, 재고·원장·멱등성 | Inventory MCP Tool | DB 동시성/Testcontainers |
| D | **하진철** | FastAPI, LangChain, MCP 통합, pgvector | AI Core/RAG/Python gRPC Client | Python OTel/Jaeger |

모든 팀원: Python AI 관련 코드·PR 1건 이상 및 정상/인가/오류 테스트 제출.

## 3. 20영업일 WBS

| 날 | 공통 게이트 | 오좌언 | 이도형 | 박천보 | 이도관 | 하진철 |
|---|---|---|---|---|---|---|
| D1 | 요구/ERD 확정 | 조직·권한 정책 | Location·단지 ID | 상태·예약 정책 | 원장·멱등성 | Tool·RAG 계약 |
| D2 | 골격/빌드 | Boot·Gateway·Compose | Asset 프로젝트 | WorkOrder 프로젝트 | Inventory 프로젝트 | FastAPI/MCP 프로젝트 |
| D3 | 기술 검증 | JWT·Platform Admin | Asset 스키마 | gRPC Java 서버 | 재고 동시성 전략 | LangChain·MCP Tool 호출 |
| D4 | API 계약 | K-apt/명부 DTO | Location API·UI | 예약 Proto/SQL | 재고 API·SQL | RAG·OTel |
| D5 | **계약 동결** | 보안/Frontend 공통 | Asset Tool Stub | RPC Spike | Inventory Tool Stub | MCP·Trace 통합 |
| D6 | 기능 구현 | 조직/가입 | 위치 CRUD | 신고 CRUD | 부품 CRUD | PDF 업로드 |
| D7 | 기능 구현 | 승인/초대 | 설비 CRUD | 배정/상태 | 입고 | 인덱싱 |
| D8 | 기능 구현 | XLSX parsing/Staging | 명부 화면/단지 | 예약 가용성 | 차감 트랜잭션 | RAG 질의 |
| D9 | 기능 연결 | 명부 Commit/가입 | Asset MCP | 예약 확정/Tool | Inventory MCP | Tool Registry |
| D10 | **개별 검증** | Member/OTP Mock | Asset CRUD·맵핑 | 예약/gRPC DB | 원장 동시성 | RAG/MCP 독립 테스트 |
| D11 | 통합 | 역할/세대 ACL | 위치·단지 검증 | AI 후보 연동 | 재고 업무 연동 | 위임 JWT 연결 |
| D12 | 통합 | 명부 자동 승인 | 동·호수 UX | 예약 UI | 작업별 차감 | LangChain 종합 응답 |
| D13 | E2E | 조직별 ACL | Asset 접근 | 예약 충돌 | 재고 멱등 | OTel Trace |
| D14 | 안정화 | FACTORY/BUILDING 정책 | 공장 Asset | 상태 예외 | 보상 원장 | RAG 문서 격리 |
| D15 | **P0 동결** | 인가 승인 | Asset 완료 | WorkOrder 완료 | Inventory 완료 | AI 통합 완료 |
| D16 | 회귀 | OTP·명부 악용 | 단지 격리 | 예약 경합 | 차감 경합 | Tool 권한 |
| D17 | 장애 | JWT 정지·회수 | API 오류 | gRPC fallback | 서비스 장애 | LLM 오류 |
| D18 | 배포 검수 | Compose/HTTPS 준비 | 반응형 | 예약 UI | 재고 UI | Jaeger·RAG |
| D19 | 문서/발표 | AWS/계약 | ERD | Proto/업무 | 정합성 | AI 시연 |
| D20 | 최종 QA | 통합 리드 | Asset 시연 | 예약 시연 | 재고 시연 | AI/Trace 시연 |

**주의:** 실제 프로젝트 시작 날짜에 따라 D1~D20 실제 날짜를 Jira Sprint와 매핑한다. **12/15~12/21의 AWS 시연 일정은 별도로 운영**하며 D20과 반드시 동일하다고 가정하지 않는다.

## 4. 기능 동결·변경 관리

- D5 API/ERD/Proto/Tool 변경은 담당자 + 팀장 리뷰 필수.
- D10 미완성 영역은 범위를 줄이되 JWT·조직 격리·예약·재고 정합성은 축소 불가.
- D15 이후 신규 기능 금지, 버그/인증·데이터 사고/시연 장애만 수정.
- Pull Request: 설계 계약 링크, DB Migration, 테스트 결과, UI 영향, Rollback 계획 기재.
- `main` 발표 릴리스 보호, 개발은 `develop` 중심 Feature branch로 통합.

## 5. 발표 시나리오

1. 가상 BUILDING 조직 등록/PLATFORM_ADMIN 승인 및 K-apt 단지 선택.
2. XLSX 2,000행 시연 명부 업로드·검증·확정 → 인증된 테스트 주민 자동 가입.
3. USER 고장 신고 → ADMIN 배정 → TECHNICIAN 방문/상태 처리.
4. AI RAG + MCP → WorkOrder gRPC 가용 슬롯 → USER UI 승인 → WorkOrder REST 예약.
5. 작업자 부품 사용 → Inventory 재고 차감 → WorkOrder 완료.
6. Jaeger 단일 요청 Trace 확인, 다른 단지 접근 차단 시연.
7. FACTORY 조직의 설비/작업 예제를 통해 산업 분기와 격리 확인.

## 6. 완료 체크리스트

- [ ] v1.0 결정/ERD/API/Auth/AI/상태/배포 문서가 PR 리뷰 완료.
- [ ] D5 빌드 + Proto/MCP SDK 기술 검증 통과.
- [ ] K-apt Mock/실제 조회와 수동 심사 후퇴 경로 검증.
- [ ] 2,000행 XLSX 중복·동시 Commit·자동 승인 공격 테스트 통과.
- [ ] 조직/단지/세대/작업 ACL 및 JWT 권한 회수 테스트 통과.
- [ ] 예약·재고 동시성·멱등성·정정 이력 테스트 통과.
- [ ] RAG 문서 격리·AI Tool 실패·gRPC fallback 테스트 통과.
- [ ] RDS `vector` 0.8.2·S3 private·도메인 HTTPS·Jaeger Trace 검증.
- [ ] 발표 동영상/로컬 Compose 장애 대비 자료 준비.
