package com.fixops.workorder.workorder.domain;

import com.fixops.workorder.common.domain.BaseEntity;
import com.fixops.workorder.report.domain.Report;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "work_order")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 조직 ID
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // 건물(단지) ID
    @Column(name = "complex_id", nullable = false)
    private Long complexId;

    // 작업 위치 ID
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    // 대상 설비 ID (선택)
    @Column(name = "asset_id")
    private Long assetId;

    // 등록자 ID(관리자)
    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    // 배정된 작업자 ID
    @Column(name = "assigned_to")
    private Long assignedTo;

    // 작업지시 제목
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 작업 상세 내용
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 우선순위
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private WorkOrderPriority priority;

    // 작업 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkOrderStatus status;

    // 연관된 작업 id
    @Column(name = "related_work_order_id")
    private Long relatedWorkOrderId;

    // 변경 시 version 증가
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    // 작업자 배정 또는 재배정
    public void assign(Long technicianId) {
        this.assignedTo = Objects.requireNonNull(technicianId);
        this.status = WorkOrderStatus.ASSIGNED;
    }

    // 작업 상태 변경
    public void changeStatus(WorkOrderStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus);
    }
}
