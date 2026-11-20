package com.fixops.workorder.report.repository;

import com.fixops.workorder.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // 유저의 신고 조회
    Page<Report> findByReportedBy(Long reportedBy, Pageable pageable);
}
