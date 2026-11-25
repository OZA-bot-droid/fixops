-- =====================================================
-- V11. 통합 승인 및 상호 일정 협의 기반 예약 시스템
-- =====================================================


-- =====================================================
-- 1. 기존 예약 데이터 확인
-- =====================================================

DO
$$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM maintenance_schedule
    ) THEN
        RAISE EXCEPTION
            'Existing maintenance schedules require migration';
END IF;
END $$;


-- =====================================================
-- 2. MAINTENANCE_SCHEDULE 컬럼 추가
-- =====================================================

ALTER TABLE maintenance_schedule

    -- 최초 예약 요청자
    ADD COLUMN requested_by BIGINT NOT NULL,

    -- 현재 또는 최종 시간 제안 ID
    ADD COLUMN current_proposal_id BIGINT,

    -- 전체 일정 협의 만료 시각
    ADD COLUMN negotiation_expires_at TIMESTAMPTZ NOT NULL,

    -- 예약 확정 시각
    ADD COLUMN confirmed_at TIMESTAMPTZ,

    -- 예약 취소 정보
    ADD COLUMN cancelled_by BIGINT,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason TEXT;


-- =====================================================
-- 3. 예약 상태 CHECK 제약 변경
-- =====================================================

ALTER TABLE maintenance_schedule
DROP
CONSTRAINT chk_schedule_status;

ALTER TABLE maintenance_schedule
    ADD CONSTRAINT chk_schedule_status
        CHECK (
            status IN (
                       'PENDING',
                       'COUNTER_PROPOSED',
                       'CONFIRMED',
                       'REJECTED',
                       'CANCELLED',
                       'COMPLETED',
                       'EXPIRED'
                )
            );


-- 최초 예약은 기술자 응답 대기 상태
ALTER TABLE maintenance_schedule
    ALTER COLUMN status SET DEFAULT 'PENDING';


-- =====================================================
-- 4. MAINTENANCE_SCHEDULE 추가 CHECK 제약
-- =====================================================

ALTER TABLE maintenance_schedule

    ADD CONSTRAINT chk_schedule_technician_id
        CHECK (technician_id > 0),

    ADD CONSTRAINT chk_schedule_requested_by
        CHECK (requested_by > 0),

    ADD CONSTRAINT chk_schedule_cancelled_by
        CHECK (
            cancelled_by IS NULL
            OR cancelled_by > 0
        ),

    ADD CONSTRAINT chk_schedule_negotiation_expiry
        CHECK (
            negotiation_expires_at > created_at
        ),

    ADD CONSTRAINT chk_schedule_confirmed_at
        CHECK (
            status NOT IN ('CONFIRMED', 'COMPLETED')
            OR confirmed_at IS NOT NULL
        ),

    ADD CONSTRAINT chk_schedule_cancellation
        CHECK (
            status <> 'CANCELLED'
            OR (
                cancelled_by IS NOT NULL
                AND cancelled_at IS NOT NULL
                AND cancellation_reason IS NOT NULL
                AND LENGTH(TRIM(cancellation_reason)) > 0
            )
        );


-- =====================================================
-- 5. 기술자 시간 중복 방지 제약 변경
-- =====================================================

ALTER TABLE maintenance_schedule
DROP
CONSTRAINT no_technician_overlap;

ALTER TABLE maintenance_schedule
    ADD CONSTRAINT no_technician_overlap EXCLUDE USING gist (
            technician_id WITH =,
            tstzrange(start_at, end_at, '[)') WITH &&
        )
        WHERE (
            status IN (
                'COUNTER_PROPOSED',
                'CONFIRMED'
            )
        );


-- =====================================================
-- 6. WorkOrder당 활성 예약 최대 1개
-- =====================================================

DROP INDEX uq_active_schedule_per_workorder;

CREATE UNIQUE INDEX uq_active_schedule_per_workorder
    ON maintenance_schedule (work_order_id) WHERE (
        status IN (
            'PENDING',
            'COUNTER_PROPOSED',
            'CONFIRMED'
        )
    );



-- =====================================================
-- 10. 조회 및 만료 처리 인덱스
-- =====================================================

-- 예약 요청자별 목록 조회
CREATE INDEX idx_schedule_requested_by
    ON maintenance_schedule (
                             requested_by,
                             created_at DESC,
                             id DESC
        );


-- 전체 일정 협의 만료 대상 조회
CREATE INDEX idx_schedule_negotiation_expiry
    ON maintenance_schedule (negotiation_expires_at) WHERE status IN (
        'PENDING',
        'COUNTER_PROPOSED'
    );