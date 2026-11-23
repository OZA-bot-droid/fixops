package com.fixops.workorder.workorder.dto;

import com.fixops.workorder.workorder.domain.WorkOrderHistory;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

// 상태 변경 이력 응답 dto
@Getter
@Builder
@AllArgsConstructor
public class WorkOrderHistoryResponseDTO {
    private Long id;
    private Long workOrderId;
    private WorkOrderStatus fromStatus;
    private WorkOrderStatus toStatus;
    private Long changedBy;
    private Instant createdAt;

    public static WorkOrderHistoryResponseDTO fromEntity(WorkOrderHistory history) {
        return WorkOrderHistoryResponseDTO.builder()
                .id(history.getId())
                .workOrderId(history.getWorkOrderId())
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .changedBy(history.getChangedBy())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
