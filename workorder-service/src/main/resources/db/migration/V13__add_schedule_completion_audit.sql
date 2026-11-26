-- 방문 완료 처리자 및 완료 시각 컬럼 추가
ALTER TABLE maintenance_schedule

    -- 방문 완료 처리자 ID (담당 기술자 ID)
    ADD COLUMN completed_by BIGINT,

    -- 실제 방문 완료 시각
    ADD COLUMN completed_at TIMESTAMPTZ;


-- 방문 완료 관련 데이터 정합성 제약조건 추가
ALTER TABLE maintenance_schedule

    -- 방문 완료 처리자 ID 유효성 검증
    -- 미완료 예약은 NULL 허용
    -- 값이 존재하는 경우 양수만 허용
    ADD CONSTRAINT chk_schedule_completed_by
        CHECK (
            completed_by IS NULL
                OR completed_by > 0
            ),

    -- 예약 상태와 방문 완료 정보의 정합성 검증
    ADD CONSTRAINT chk_schedule_completion
        CHECK (
            (
                status = 'COMPLETED'
                AND completed_by IS NOT NULL
                AND completed_at IS NOT NULL
                AND completed_by = technician_id
                AND completed_at >= start_at
            )
            OR
            (
                status <> 'COMPLETED'
                AND completed_by IS NULL
                AND completed_at IS NULL
            )
        );