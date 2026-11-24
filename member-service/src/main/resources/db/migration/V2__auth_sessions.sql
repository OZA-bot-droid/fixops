-- ============================================================================
-- 7. 인증(로그인) 세션 테이블:
--    리프레시 토큰 로테이션(재발급)과 독립적으로 유지되는 상위 로그인 세션 관리
-- ============================================================================
CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,                              -- 인증 세션 고유 식별자 (UUID)
    member_id BIGINT NOT NULL REFERENCES members(id), -- 세션 소유 회원 ID (members 참조)
    expires_at TIMESTAMPTZ NOT NULL,                  -- 전체 로그인 세션 절대 만료 일시
    revoked BOOLEAN NOT NULL DEFAULT FALSE,           -- 세션 강제 만료(로그아웃/보안 차단) 여부
    revoked_at TIMESTAMPTZ,                           -- 세션이 강제 만료된 일시
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()     -- 최초 로그인(세션 생성) 일시
);

-- 특정 회원의 전체 로그인 세션 목록 및 활성 상태 조회 성능 향상
CREATE INDEX idx_auth_sessions_member
    ON auth_sessions(member_id);

-- ============================================================================
-- 8. 리프레시 토큰 테이블 확장: 모든 리프레시 토큰을 상위 인증 세션에 필수 귀속
-- ============================================================================
ALTER TABLE refresh_sessions
    ADD COLUMN auth_session_id UUID NOT NULL          -- 연결된 상위 인증 세션 ID (필수값, auth_sessions 참조)
    REFERENCES auth_sessions(id);

-- 특정 인증 세션에 속한 리프레시 토큰 일괄 조회 및 폐기 성능 향상
CREATE INDEX idx_refresh_auth_session
    ON refresh_sessions(auth_session_id);
