package com.fixops.workorder.booking.domain;


import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "schedule_proposal")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // MaintenanceSchedule ID
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;


    // 시간 제안자
    @Column(name = "proposed_by", nullable = false)
    private Long proposedBy;


    // 제안 시작 시각
    @Column(name = "start_at", nullable = false)
    private Instant startAt;


    // 제안 종료 시각
    @Column(name = "end_at", nullable = false)
    private Instant endAt;


    // 개별 Proposal 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScheduleProposalStatus status = ScheduleProposalStatus.PENDING;


    // 시간 제안 사유
    @Column(columnDefinition = "TEXT")
    private String reason;


    // 응답자
    @Column(name = "responded_by")
    private Long respondedBy;


    // 응답 시각
    @Column(name = "responded_at")
    private Instant respondedAt;


    // 거절 또는 재제안 사유
    @Column(name = "response_reason", columnDefinition = "TEXT")
    private String responseReason;


    // 개별 응답 기한
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;


    // 제안 생성 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    // =================================================
    // 1. 시간 제안 수락
    // =================================================

    public void accept(Long userId, Instant now) {

        validateResponse(userId, now);

        this.status = ScheduleProposalStatus.ACCEPTED;

        recordResponse(userId, now, null);
    }


    // =================================================
    // 2. 시간 제안 거절
    // =================================================

    public void reject(Long userId, String reason, Instant now) {

        validateResponse(userId, now);

        if (reason == null || reason.isBlank()) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = ScheduleProposalStatus.REJECTED;

        recordResponse(userId, now, reason.trim());
    }


    // =================================================
    // 3. 새로운 시간 제안으로 대체
    // =================================================
    // 상대방이 기존 제안에 동의하지 않고 새로운 시간으로 제안하는 경우
    public void supersede(Long userId, String reason, Instant now) {

        validateResponse(userId, now);

        this.status = ScheduleProposalStatus.SUPERSEDED;

        recordResponse(userId, now, reason);
    }


    // =================================================
    // 4. 예약 철회 및 취소
    // =================================================

    public void cancel(Long userId, String reason, Instant now) {

        validatePendingStatus();

        if (userId == null || userId <= 0 || now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        this.status = ScheduleProposalStatus.CANCELLED;

        recordResponse(userId, now, reason);
    }


    // =================================================
    // 5. 개별 제안 만료
    // =================================================

    public void expire(Instant now) {

        validatePendingStatus();

        if (now == null || now.isBefore(expiresAt)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        this.status = ScheduleProposalStatus.EXPIRED;
    }


    // =================================================
    // 6. 개별 응답 기한 검증 확인
    // => now가 expiresAt보다 늦거나 같으면 만료
    // =================================================

    public boolean isExpired(Instant now) {

        return !now.isBefore(expiresAt);
    }


    // =================================================
    // 7. 공통 검증
    // =================================================

    private void validateResponse(Long userId, Instant now) {

        validatePendingStatus();

        if (userId == null || userId <= 0 || now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 자신이 제안한 시간을 스스로 수락·거절할 수 없음
        if (userId.equals(proposedBy)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        // 개별 응답 기한 초과
        if (isExpired(now)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }


    private void validatePendingStatus() {

        if (!status.isPending()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }


    private void recordResponse(Long userId, Instant now, String reason) {

        this.respondedBy = userId;
        this.respondedAt = now;
        this.responseReason = reason;
    }
}