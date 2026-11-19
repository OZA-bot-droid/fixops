# FixOps Figma 기반 프론트엔드 (팀장 영역)

원본: https://www.figma.com/design/wDLUWz66VEZcNBlAboMZ2w

- Product Screens: 01 로그인, 02 회원가입, 03 비밀번호 재설정, 04 조직 생성, 05 조직 가입, 06 회원 역할 관리, 07 프로필, 08 감사 로그. 운영 레이아웃은 Product Screens의 244px 사이드바를 기반으로 구현.
- Design System: primary #3069F6, sidebar #111C32, background #F4F7FC, text #17243C, border #E4EAF3.
- Modal Library: 확인/취소 패턴을 공통 Modal 컴포넌트로 구현.
- 브라우저: npm install 후 npm run dev. API는 /api -> http://localhost:8080 (Vite proxy).

## 실제 API 연동
- 로그인, 회원가입, /members/me 복원, 조직 개설 신청, 내 조직 신청, 플랫폼 조직 승인/반려: 기존 Member API 계약 유지.
- 이메일 인증, 비밀번호 재설정, 조직 초대, 회원 역할 수정, 감사 로그 조회: 백엔드 API 미구현. 화면은 안내 또는 읽기 전용이며 가짜 저장하지 않음.

## 보안/권한
- 클라이언트 라우터 가드는 UI 편의용. 반드시 Gateway 및 서비스에서 인가를 검증해야 함.
- Refresh Token은 기존 스캐폴드의 개발용 sessionStorage이며, 운영 단계에서는 HttpOnly Secure 쿠키 + CSRF 방어 설계가 필요.

## 적용
기존 `frontend/`의 변경 대상과 diff 확인 후 `feature/frontend-common`에서 공통 UI 및 Member 화면을 반영하세요. 파일 전체를 무조건 덮어쓰지 말고 다른 팀원 작업과 충돌 확인 필요.
