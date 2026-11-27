package com.fixops.workorder.booking.dto;

import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;

import java.time.Instant;

// 조회 전용
public record ScheduleReadDTO(

        Long id,

        Long workOrderId,

        String workOrderTitle,

        Long organizationId,

        Long complexId,

        Long locationId,

        Long assetId,

        WorkOrderStatus workOrderStatus,

        Long technicianId,

        Long requestedBy,

        Instant startAt,

        Instant endAt,

        MaintenanceScheduleStatus status,

        Long currentProposalId,

        ScheduleProposalResponseDTO currentProposal,

        Long expectedResponderId,

        Instant negotiationExpiresAt,

        Instant confirmedAt,

        Long cancelledBy,

        Instant cancelledAt,

        String cancellationReason,

        Long completedBy,

        Instant completedAt,

        Instant createdAt,

        Instant updatedAt,

        // 조회한 사용자가 현재 제안에 응답 가능한지
        boolean canRespond,

        // 조회한 사용자가 예약 취소 가능한지
        boolean canCancel,

        // 조회한 사용자가 방문 완료 가능한지
        boolean canComplete,

        // 실제 응답 기한이 이미 경과했는지
        boolean responseExpired

) {
}