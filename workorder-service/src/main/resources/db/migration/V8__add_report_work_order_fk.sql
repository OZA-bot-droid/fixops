-- 실제 존재하는 work_order 테이블의 id만 참조 FK
ALTER TABLE report
    ADD CONSTRAINT fk_report_work_order
        FOREIGN KEY (work_order_id)
            REFERENCES work_order (id)
            ON DELETE RESTRICT;