package com.fixops.workorder.report.repository;

import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    Optional<Report> findByIdAndReportedBy(Long id, Long reportedBy);

    // =======================================
    // SELECT FOR UPDATE 사용


    // 작업 생성: 같은 신고의 중복 WorkOrder 생성 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findForWorkOrderConversion(@Param("id") Long id);


    // 신고 수정·취소: WorkOrder 생성과 동시 실행될 때 같은 행 잠금
    // 일반 사용자가 자신이 작성한 신고를 수정하거나 취소할 때
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Report r
            WHERE r.id = :id
              AND r.reportedBy = :reportedBy
            """)
    Optional<Report> findEditableByIdAndReportedByForUpdate(@Param("id") Long id, @Param("reportedBy") Long reportedBy);
    // =======================================
}
