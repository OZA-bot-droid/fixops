package com.fixops.workorder.workorder.repository;

import com.fixops.workorder.workorder.domain.WorkOrder;
import com.fixops.workorder.workorder.domain.WorkOrderPriority;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 같은 조직·단지 내 작업지시 목록
    Page<WorkOrder> findByOrganizationIdAndComplexId(Long organizationId, Long complexId, Pageable pageable);

    // 작업지시 상세 조회
    Optional<WorkOrder> findByIdAndOrganizationId(Long id, Long organizationId);

    // 작업자 본인에게 배정된 작업 조회
    Page<WorkOrder> findByOrganizationIdAndComplexIdAndAssignedTo(Long organizationId, Long complexId, Long assignedTo, Pageable pageable);

    // 조직 전체 및 필터별 작업지시 목록 조회
    // 세부조건 X일 경우, 조직 id 기준으로 검색
    @Query("""
            SELECT w
            FROM WorkOrder w
            WHERE w.organizationId = :organizationId
              AND (
                  :complexId IS NULL
                  OR w.complexId = :complexId
              )
              AND (
                  :locationId IS NULL
                  OR w.locationId = :locationId
              )
              AND (
                  :assetId IS NULL
                  OR w.assetId = :assetId
              )
              AND (
                  :status IS NULL
                  OR w.status = :status
              )
              AND (
                  :priority IS NULL
                  OR w.priority = :priority
              )
              AND (
                  :assignedTo IS NULL
                  OR w.assignedTo = :assignedTo
              )
              AND (
                  :keyword = ''
                  OR LOWER(w.title) LIKE
                     LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<WorkOrder> searchWorkOrders(
            @Param("organizationId") Long organizationId,
            @Param("complexId") Long complexId,
            @Param("locationId") Long locationId,
            @Param("assetId") Long assetId,
            @Param("status") WorkOrderStatus status,
            @Param("priority") WorkOrderPriority priority,
            @Param("assignedTo") Long assignedTo,
            @Param("keyword") String keyword,
            Pageable pageable
    );


    // 예약 생성 및 상태 변경을 위한 작업지시 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM WorkOrder w
            WHERE w.id = :workOrderId
            """)
    Optional<WorkOrder> findByIdForUpdate(@Param("workOrderId") Long workOrderId);
}
