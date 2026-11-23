package com.fixops.workorder.workorder.dto;

import com.fixops.workorder.workorder.domain.WorkOrder;
import com.fixops.workorder.workorder.domain.WorkOrderPriority;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 작업지시 응답 dto
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderResponseDTO {

    private Long id;
    private Long organizationId;
    private Long complexId;
    private Long locationId;
    private Long assetId;
    private Long relatedWorkOrderId;
    private String title;
    private String description;
    private Long reportedBy;
    private Long assignedTo;
    private WorkOrderPriority priority;
    private WorkOrderStatus status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static WorkOrderResponseDTO fromEntity(WorkOrder workOrder) {
        return WorkOrderResponseDTO.builder()
                .id(workOrder.getId())
                .organizationId(workOrder.getOrganizationId())
                .complexId(workOrder.getComplexId())
                .locationId(workOrder.getLocationId())
                .assetId(workOrder.getAssetId())
                .relatedWorkOrderId(workOrder.getRelatedWorkOrderId())
                .title(workOrder.getTitle())
                .description(workOrder.getDescription())
                .reportedBy(workOrder.getReportedBy())
                .assignedTo(workOrder.getAssignedTo())
                .priority(workOrder.getPriority())
                .status(workOrder.getStatus())
                .version(workOrder.getVersion())
                .createdAt(workOrder.getCreatedAt())
                .updatedAt(workOrder.getUpdatedAt())
                .build();
    }
}