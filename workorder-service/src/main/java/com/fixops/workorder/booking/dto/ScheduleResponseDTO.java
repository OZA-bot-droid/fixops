package com.fixops.workorder.booking.dto;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ScheduleResponseDTO { // 예약 정보를 반환하는 DTO

    private Long id;

    private Long workOrderId;

    private Long technicianId;

    private Long requestedBy;

    private Instant startAt;

    private Instant endAt;

    private MaintenanceScheduleStatus status;

    private Long currentProposalId;

    private ScheduleProposalResponseDTO currentProposal;

    // 현재 응답해야 하는 사용자 ID(현재 누구의 응답을 기다리고 있는지 프론트에서 확인하는 용)
    private Long expectedResponderId;

    private Instant negotiationExpiresAt;

    private Instant confirmedAt;

    private Long cancelledBy;

    private Instant cancelledAt;

    private String cancellationReason;

    private Instant createdAt;

    private Instant updatedAt;

    private Long completedBy;

    private Instant completedAt;


    public static ScheduleResponseDTO fromEntity(MaintenanceSchedule schedule) {

        return fromEntity(schedule, null);
    }


    public static ScheduleResponseDTO fromEntity(MaintenanceSchedule schedule, ScheduleProposal proposal) {

        Long expectedResponderId = switch (schedule.getStatus()) {

            case PENDING -> schedule.getTechnicianId();

            case COUNTER_PROPOSED -> schedule.getRequestedBy();

            default -> null;
        };


        return ScheduleResponseDTO.builder()
                                .id(schedule.getId())
                                .workOrderId(schedule.getWorkOrderId())
                                .technicianId(schedule.getTechnicianId())
                                .requestedBy(schedule.getRequestedBy())
                                .startAt(schedule.getStartAt())
                                .endAt(schedule.getEndAt())
                                .status(schedule.getStatus())
                                .currentProposalId(schedule.getCurrentProposalId())
                                .currentProposal(proposal == null ? null : ScheduleProposalResponseDTO.fromEntity(proposal))
                                .expectedResponderId(expectedResponderId)
                                .negotiationExpiresAt(schedule.getNegotiationExpiresAt())
                                .confirmedAt(schedule.getConfirmedAt())
                                .cancelledBy(schedule.getCancelledBy())
                                .cancelledAt(schedule.getCancelledAt())
                                .cancellationReason(schedule.getCancellationReason())
                                .createdAt(schedule.getCreatedAt())
                                .updatedAt(schedule.getUpdatedAt())
                                .completedBy(schedule.getCompletedBy())
                                .completedAt(schedule.getCompletedAt())
                                .build();
    }
}