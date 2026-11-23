package com.fixops.workorder.workorder.repository;

import com.fixops.workorder.workorder.domain.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 같은 조직·단지 내 작업지시 목록
    Page<WorkOrder> findByOrganizationIdAndComplexId(Long organizationId, Long complexId, Pageable pageable);
}
