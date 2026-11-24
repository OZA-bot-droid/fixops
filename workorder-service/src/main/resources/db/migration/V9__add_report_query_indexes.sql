-- Report 조회 인덱스

-- 본인 신고 최신순 조회
CREATE INDEX idx_report_reported_by_created
    ON report (
               reported_by,
               created_at DESC,
               id DESC
        );

-- 관리자 조직·단지별 신고 조회
CREATE INDEX idx_report_org_complex_created
    ON report (
               organization_id,
               complex_id,
               created_at DESC,
               id DESC
        );

-- 관리자 조직·단지별 상태 필터링
CREATE INDEX idx_report_org_complex_status
    ON report (
               organization_id,
               complex_id,
               status,
               created_at DESC,
               id DESC
        );