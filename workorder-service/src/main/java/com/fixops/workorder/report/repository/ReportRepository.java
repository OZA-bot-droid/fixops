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

    // workOrderId로 해당 report 조회
    Optional<Report> findByWorkOrderId(Long workOrderId);

    // 관리자 신고 상세 조회
    Optional<Report> findByIdAndOrganizationId(Long id, Long organizationId);

    // 관리자 신고 상세 조회
    Optional<Report> findByIdAndOrganizationIdAndComplexId(Long id, Long organizationId, Long complexId);


    // 관리자용 신고 목록 조회
    // 조직 전체 및 필터별 신고 목록 조회 - 조직 조건은 항상 적용하고 나머지 조건은 입력된 경우에만 적용
    @Query("""
            SELECT r
            FROM Report r
            WHERE r.organizationId = :organizationId
              AND (
                  :complexId IS NULL
                  OR r.complexId = :complexId
              )
              AND (
                  :locationId IS NULL
                  OR r.locationId = :locationId
              )
              AND (
                  :status IS NULL
                  OR r.status = :status
              )
              AND (
                  :keyword = ''
                  OR LOWER(r.title) LIKE
                     LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<Report> findAdminReports(
            @Param("organizationId") Long organizationId,
            @Param("complexId") Long complexId,
            @Param("locationId") Long locationId,
            @Param("status") ReportStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // =======================================
    // SELECT FOR UPDATE 사용


    // 작업 생성: 같은 신고의 중복 WorkOrder 생성 방지
    // 신고 → WorkOrder 생성 시 잠금
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


    // WorkOrder 상태 변경 시 연결된 신고 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Report r
            WHERE r.workOrderId = :workOrderId
            """)
    Optional<Report> findByWorkOrderIdForUpdate(@Param("workOrderId") Long workOrderId);
    // =======================================
}
