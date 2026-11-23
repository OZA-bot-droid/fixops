package com.fixops.workorder.report.repository;

import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // 유저의 신고 조회
    Page<Report> findByReportedBy(Long reportedBy, Pageable pageable);

    // 제목으로 검색
    Page<Report> findByReportedByAndTitleContainingIgnoreCase(Long reportedBy, String title, Pageable pageable);

    // 상태로 필터링
    Page<Report> findByReportedByAndStatus(Long reportedBy, ReportStatus status, Pageable pageable);

    // 제목 + 상태로 검색
    Page<Report> findByReportedByAndTitleContainingIgnoreCaseAndStatus(Long reportedBy, String title, ReportStatus status, Pageable pageable);

    // 기존 ReportRepository 내부에 추가
    Optional<Report>  findByIdAndReportedBy(Long id, Long reportedBy);
}
