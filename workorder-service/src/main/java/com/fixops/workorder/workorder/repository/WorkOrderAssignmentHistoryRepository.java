package com.fixops.workorder.workorder.repository;

import com.fixops.workorder.workorder.domain.WorkOrderAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderAssignmentHistoryRepository extends JpaRepository<WorkOrderAssignmentHistory, Long> {

    // 작업자 배정·재배정 이력을 조회하고, 최신 이력부터 내림차순으로 정렬
    List<WorkOrderAssignmentHistory> findByWorkOrderIdOrderByCreatedAtDescIdDesc(Long workOrderId);
}