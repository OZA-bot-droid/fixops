package com.fixops.workorder.workorder.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "work_order_assignment_history")
@Getter
@NoArgsConstructor
public class WorkOrderAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작업 지시 id
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    // 변경 전 작업자 id
    @Column(name = "from_technician_id")
    private Long fromTechnicianId;

    // 변경 후 작업자 id
    @Column(name = "to_technician_id", nullable = false)
    private Long toTechnicianId;

    // 작업자를 변경한 관리자 ID
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WorkOrderAssignmentHistory(Long workOrderId, Long fromTechnicianId, Long toTechnicianId, Long changedBy) {
        this.workOrderId = workOrderId;
        this.fromTechnicianId = fromTechnicianId;
        this.toTechnicianId = toTechnicianId;
        this.changedBy = changedBy;
    }
}