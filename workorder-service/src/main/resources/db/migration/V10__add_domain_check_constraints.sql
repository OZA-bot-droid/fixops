-- 신고의 필수 ID 검증(음수 방지)
ALTER TABLE report
    ADD CONSTRAINT chk_report_positive_ids
        CHECK (
            organization_id > 0
                AND complex_id > 0
                AND location_id > 0
                AND reported_by > 0
            );

-- 작업지시의 필수 ID 검증(음수 방지)
ALTER TABLE work_order
    ADD CONSTRAINT chk_work_order_positive_ids
        CHECK (
            organization_id > 0
                AND complex_id > 0
                AND location_id > 0
                AND reported_by > 0
                AND (
                asset_id IS NULL
                    OR asset_id > 0
                )
                AND (
                assigned_to IS NULL
                    OR assigned_to > 0
                )
            );

-- 배정·진행·완료 상태는 작업자 필수
ALTER TABLE work_order
    ADD CONSTRAINT chk_work_order_assigned_status
        CHECK (
            status NOT IN (
                           'ASSIGNED',
                           'IN_PROGRESS',
                           'COMPLETED'
                )
                OR assigned_to IS NOT NULL
            );