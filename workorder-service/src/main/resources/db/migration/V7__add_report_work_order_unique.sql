-- 두 Report가 동일한 WorkOrder를 가리키지 못하게 하는 제약
ALTER TABLE report
    ADD CONSTRAINT uq_report_work_order_id
        UNIQUE (work_order_id);