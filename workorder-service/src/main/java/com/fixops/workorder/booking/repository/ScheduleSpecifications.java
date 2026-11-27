package com.fixops.workorder.booking.repository;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.dto.ScheduleSearchDTO;
import com.fixops.workorder.workorder.domain.WorkOrder;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ScheduleSpecifications {

    private ScheduleSpecifications() {
    }


    // 관리자, 기술자, 요청자 조회 공통 검색 조건
    // Specification은 실제 입력된 조건만 동적으로 추가함.
    public static Specification<MaintenanceSchedule> search(

            ScheduleSearchDTO dto,

            Long creatorAdminId,

            Long technicianId,

            Long requesterId

    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();


            // =====================================
            // 1. WorkOrder 기준 검색 범위
            // =====================================

            Subquery<Long> workOrderQuery = query.subquery(Long.class);

            Root<WorkOrder> workOrder = workOrderQuery.from(WorkOrder.class);

            List<Predicate> workOrderPredicates = new ArrayList<>();


            // Schedule과 WorkOrder 연결 조건
            workOrderPredicates.add(cb.equal(workOrder.get("id"), root.get("workOrderId")));


            // 필수 조직 검색
            workOrderPredicates.add(cb.equal(workOrder.get("organizationId"), dto.getOrganizationId()));


            // 선택적 단지 조건
            if (dto.getComplexId() != null) {

                workOrderPredicates.add(cb.equal(workOrder.get("complexId"), dto.getComplexId()));
            }


            // 선택적 위치 조건
            if (dto.getLocationId() != null) {

                workOrderPredicates.add(cb.equal(workOrder.get("locationId"), dto.getLocationId()));
            }


            // 선택적 설비 조건
            if (dto.getAssetId() != null) {

                workOrderPredicates.add(cb.equal(workOrder.get("assetId"), dto.getAssetId()));
            }


            // Member 연동 전 임시 관리자 조회 범위 제한
            // 자신이 생성한 WorkOrder의 예약만 조회
            if (creatorAdminId != null) {

                workOrderPredicates.add(cb.equal(workOrder.get("reportedBy"), creatorAdminId));
            }


            workOrderQuery.select(workOrder.<Long>get("id")).where(workOrderPredicates.toArray(Predicate[]::new));


            predicates.add(cb.exists(workOrderQuery));


            // =====================================
            // 2. 기술자 및 요청자 조건
            // =====================================

            if (technicianId != null) {

                predicates.add(cb.equal(root.get("technicianId"), technicianId));
            }


            if (requesterId != null) {

                predicates.add(cb.equal(root.get("requestedBy"), requesterId));
            }


            // =====================================
            // 3. 예약 상태 조건
            // =====================================

            if (dto.getStatus() != null) {

                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            }


            // =====================================
            // 4. 예약 기간 중복 조건
            // =====================================

            // 예약 종료 시각 > 검색 시작 시각
            if (dto.getFromAt() != null) {

                predicates.add(cb.greaterThan(root.<Instant>get("endAt"), dto.getFromAt()));
            }


            // 예약 시작 시각 < 검색 종료 시각
            if (dto.getToAt() != null) {

                predicates.add(cb.lessThan(root.<Instant>get("startAt"), dto.getToAt()));
            }


            // =====================================
            // 5. 모든 검색 조건 결합
            // =====================================

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}