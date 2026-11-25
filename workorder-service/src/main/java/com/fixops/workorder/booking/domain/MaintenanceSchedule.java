package com.fixops.workorder.booking.domain;

import com.fixops.workorder.common.domain.BaseEntity;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "maintenance_schedule")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 작업지시 ID
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;


    // 담당 기술자 ID
    @Column(name = "technician_id", nullable = false)
    private Long technicianId;


    // 최초 예약 요청자 ID
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;


    // 현재 제안 또는 확정 시작 시각
    @Column(name = "start_at", nullable = false)
    private Instant startAt;


    // 현재 제안 또는 확정 종료 시각
    @Column(name = "end_at", nullable = false)
    private Instant endAt;


    // 예약 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MaintenanceScheduleStatus status = MaintenanceScheduleStatus.PENDING;


    // 현재 또는 최종 제안 ID
    @Column(name = "current_proposal_id")
    private Long currentProposalId;


    // 전체 일정 협의 만료 시각
    @Column(name = "negotiation_expires_at", nullable = false)
    private Instant negotiationExpiresAt;


    // 예약 확정 시각
    @Column(name = "confirmed_at")
    private Instant confirmedAt;


    // 예약 취소자
    @Column(name = "cancelled_by")
    private Long cancelledBy;


    // 예약 취소 시각
    @Column(name = "cancelled_at")
    private Instant cancelledAt;


    // 예약 취소 사유
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;


    // =================================================
    // 1. 최초 Proposal 연결
    // =================================================

    public void initializeProposal(Long proposalId) {

        if (status != MaintenanceScheduleStatus.PENDING || this.currentProposalId != null) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        validateProposalId(proposalId);

        this.currentProposalId = proposalId;
    }


    // =================================================
    // 2. 기술자 대체 시간 제안
    // =================================================

    public void counterPropose(Long proposalId, Instant startAt, Instant endAt) {

        if (status != MaintenanceScheduleStatus.PENDING) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        updateCurrentProposal(proposalId, startAt, endAt);

        this.status = MaintenanceScheduleStatus.COUNTER_PROPOSED;
    }


    // =================================================
    // 3. 예약 요청자 시간 재제안
    // =================================================

    public void rePropose(Long proposalId, Instant startAt, Instant endAt) {

        if (status != MaintenanceScheduleStatus.COUNTER_PROPOSED) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        updateCurrentProposal(proposalId, startAt, endAt);

        this.status = MaintenanceScheduleStatus.PENDING;
    }


    // =================================================
    // 4. 예약 확정
    // =================================================

    public void confirm(Long proposalId, Instant now) {

        validateNegotiatingStatus();
        validateCurrentProposal(proposalId);
        validateNegotiationDeadline(now);

        this.status = MaintenanceScheduleStatus.CONFIRMED;
        this.confirmedAt = now;
    }


    // =================================================
    // 5. 예약 거절
    // =================================================

    public void reject() {

        validateNegotiatingStatus();

        this.status = MaintenanceScheduleStatus.REJECTED;
    }


    // =================================================
    // 6. 예약 철회 및 취소
    // =================================================

    public void cancel(Long userId, String reason, Instant now) {

        if (!status.isActive()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        if (userId == null || userId <= 0 || reason == null || reason.isBlank() || now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = MaintenanceScheduleStatus.CANCELLED;
        this.cancelledBy = userId;
        this.cancelledAt = now;
        this.cancellationReason = reason.trim();
    }


    // =================================================
    // 7. 방문 완료
    // =================================================

    public void complete(Instant now) {

        if (status != MaintenanceScheduleStatus.CONFIRMED) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        if (now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 방문 시작 전 완료 처리 방지
        if (now.isBefore(startAt)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        this.status = MaintenanceScheduleStatus.COMPLETED;
    }


    // =================================================
    // 8. 예약 협의 만료
    // =================================================

    public void expire() {

        validateNegotiatingStatus();

        this.status = MaintenanceScheduleStatus.EXPIRED;
    }


    // =================================================
    // 9. 공통 검증 및 내부 메서드
    // =================================================

    private void updateCurrentProposal(Long proposalId, Instant startAt, Instant endAt) {

        validateProposalId(proposalId);
        validateTime(startAt, endAt);

        if (proposalId.equals(currentProposalId)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        this.currentProposalId = proposalId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    // 이전 Proposal 수락 방지
    public void validateCurrentProposal(Long proposalId) {

        validateProposalId(proposalId);

        if (!proposalId.equals(currentProposalId)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    // 전체 협의 만료 후 확정 방지
    public void validateNegotiationDeadline(Instant now) {

        if (now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!now.isBefore(negotiationExpiresAt)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    // 이미 종료된 예약 확정 방지
    private void validateNegotiatingStatus() {

        if (!status.isNegotiating()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }


    private void validateProposalId(Long proposalId) {

        if (proposalId == null || proposalId <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    private void validateTime(Instant startAt, Instant endAt) {

        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}