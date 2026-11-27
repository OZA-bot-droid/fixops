package com.fixops.workorder.booking.repository;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;


public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long>, JpaSpecificationExecutor<MaintenanceSchedule> {
// JpaSpecificationExecutor => 동적 쿼리를 쉽게 작성할 수 있도록 돕는 인터페이스


    // 작업지시별 예약 이력 조회(WorkOrder에 활성 예약이 존재하는지 조회)
    List<MaintenanceSchedule> findByWorkOrderIdOrderByCreatedAtDescIdDesc(Long workOrderId);


    // 예약 요청자 본인의 예약 목록 조회
    Page<MaintenanceSchedule> findByRequestedBy(Long requestedBy, Pageable pageable);


    // 작업지시별 현재 활성 예약 조회
    Optional<MaintenanceSchedule> findByWorkOrderIdAndStatusIn(Long workOrderId, Collection<MaintenanceScheduleStatus> statuses);


    // 작업지시별 활성 예약 존재 여부(중복 활성 예약 생성 방지)
    boolean existsByWorkOrderIdAndStatusIn(Long workOrderId, Collection<MaintenanceScheduleStatus> statuses);


    // 예약 ID 기준 비관적 락 조회
    // 수락·거절·취소·재제안
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM MaintenanceSchedule s
            WHERE s.id = :scheduleId
            """)
    Optional<MaintenanceSchedule> findByIdForUpdate(@Param("scheduleId") Long scheduleId);


    // 작업지시별 활성 예약 비관적 락 조회
    // WorkOrder 재배정·취소
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM MaintenanceSchedule s
            WHERE s.workOrderId = :workOrderId
              AND s.status IN :statuses
            """)
    Optional<MaintenanceSchedule> findActiveScheduleForUpdate(@Param("workOrderId") Long workOrderId,
                                                              @Param("statuses") Collection<MaintenanceScheduleStatus> statuses);


    // 기술자 시간 중복 여부 조회(최초 요청 시간 충돌 사전 검사)
    @Query("""
            SELECT CASE
                WHEN COUNT(s) > 0 THEN true
                ELSE false
            END
            FROM MaintenanceSchedule s
            WHERE s.technicianId = :technicianId
              AND s.status IN :statuses
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            """)
    boolean existsOverlappingSchedule(@Param("technicianId") Long technicianId,
                                    @Param("statuses") Collection<MaintenanceScheduleStatus> statuses,
                                    @Param("startAt") Instant startAt,
                                    @Param("endAt") Instant endAt);


    // 현재 예약을 제외한 시간 중복 여부 조회(대체 제안·재제안·예약 확정 용)
    @Query("""
            SELECT CASE
                WHEN COUNT(s) > 0 THEN true
                ELSE false
            END
            FROM MaintenanceSchedule s
            WHERE s.technicianId = :technicianId
              AND s.status IN :statuses
              AND s.startAt < :endAt
              AND s.endAt > :startAt
              AND s.id <> :excludedScheduleId
            """)
    boolean existsOverlappingScheduleExcludingSelf(@Param("technicianId") Long technicianId,
                                                @Param("statuses") Collection<MaintenanceScheduleStatus> statuses,
                                                @Param("startAt") Instant startAt,
                                                @Param("endAt") Instant endAt,
                                                @Param("excludedScheduleId") Long excludedScheduleId);


    // 특정 기간 내 기술자 점유 예약 조회(예약 후보 시간 계산 및 만료 점유 확인)
    @Query("""
            SELECT s
            FROM MaintenanceSchedule s
            WHERE s.technicianId = :technicianId
              AND s.status IN :statuses
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            ORDER BY s.startAt ASC, s.id ASC
            """)
    List<MaintenanceSchedule> findOccupiedSchedules(@Param("technicianId") Long technicianId,
                                                @Param("statuses") Collection<MaintenanceScheduleStatus> statuses,
                                                @Param("startAt") Instant startAt,
                                                @Param("endAt") Instant endAt);


    // 전체 협의 만료 대상 ID 조회
    @Query("""
            SELECT s.id
            FROM MaintenanceSchedule s
            WHERE s.status IN :statuses
              AND s.negotiationExpiresAt <= :now
            ORDER BY s.negotiationExpiresAt ASC, s.id ASC
            """)
    List<Long> findNegotiationExpiredScheduleIds(@Param("statuses") Collection<MaintenanceScheduleStatus> statuses,
                                                @Param("now") Instant now,
                                                Pageable pageable);


    // 관리자 및 기술자 예약 목록 검색
    @Query("""
            SELECT s
            FROM MaintenanceSchedule s
            WHERE EXISTS (
                SELECT w.id
                FROM WorkOrder w
                WHERE w.id = s.workOrderId
                  AND w.organizationId = :organizationId
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
            )
              AND (
                  :technicianId IS NULL
                  OR s.technicianId = :technicianId
              )
              AND (
                  :status IS NULL
                  OR s.status = :status
              )
              AND (
                  :fromAt IS NULL
                  OR s.endAt > :fromAt
              )
              AND (
                  :toAt IS NULL
                  OR s.startAt < :toAt
              )
            """)
    Page<MaintenanceSchedule> searchSchedules(@Param("organizationId") Long organizationId,
                                            @Param("complexId") Long complexId,
                                            @Param("locationId") Long locationId,
                                            @Param("assetId") Long assetId,
                                            @Param("technicianId") Long technicianId,
                                            @Param("status") MaintenanceScheduleStatus status,
                                            @Param("fromAt") Instant fromAt,
                                            @Param("toAt") Instant toAt,
                                            Pageable pageable);

    // 예약 ID를 통해 WorkOrder ID만 조회
    @Query("""
            SELECT s.workOrderId
            FROM MaintenanceSchedule s
            WHERE s.id = :scheduleId
            """)
    Optional<Long> findWorkOrderIdByScheduleId(@Param("scheduleId") Long scheduleId);
}