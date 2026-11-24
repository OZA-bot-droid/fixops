package com.fixops.workorder.workorder.dto;

import com.fixops.workorder.workorder.domain.WorkOrderAssignmentHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAssignmentHistoryResponseDTO {

    private Long id;
    private Long workOrderId;

    private Long fromTechnicianId;
    private Long toTechnicianId;

    private Long changedBy;
    private Instant createdAt;

    public static WorkOrderAssignmentHistoryResponseDTO
    fromEntity(WorkOrderAssignmentHistory history) {

        return WorkOrderAssignmentHistoryResponseDTO
                .builder()
                .id(history.getId())
                .workOrderId(history.getWorkOrderId())
                .fromTechnicianId(
                        history.getFromTechnicianId()
                )
                .toTechnicianId(
                        history.getToTechnicianId()
                )
                .changedBy(history.getChangedBy())
                .createdAt(history.getCreatedAt())
                .build();
    }
}