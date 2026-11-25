package com.fixops.workorder.booking.dto;

import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ScheduleProposalResponseDTO {
    // 응답 DTO
    /*  [사용처]
        현재 Proposal 조회
        전체 일정 협의 이력 조회
        기술자 대체 제안 결과
        요청자 재제안 결과
        Proposal 응답 상태 확인
    */


    private Long id;

    private Long scheduleId;

    private Long proposedBy;

    private Instant startAt;

    private Instant endAt;

    private ScheduleProposalStatus status;

    private String reason;

    private Long respondedBy;

    private Instant respondedAt;

    private String responseReason;

    private Instant expiresAt;

    private Instant createdAt;


    public static ScheduleProposalResponseDTO fromEntity(ScheduleProposal proposal) {

        return ScheduleProposalResponseDTO.builder()
                                        .id(proposal.getId())
                                        .scheduleId(proposal.getScheduleId())
                                        .proposedBy(proposal.getProposedBy())
                                        .startAt(proposal.getStartAt())
                                        .endAt(proposal.getEndAt())
                                        .status(proposal.getStatus())
                                        .reason(proposal.getReason())
                                        .respondedBy(proposal.getRespondedBy())
                                        .respondedAt(proposal.getRespondedAt())
                                        .responseReason(proposal.getResponseReason())
                                        .expiresAt(proposal.getExpiresAt())
                                        .createdAt(proposal.getCreatedAt())
                                        .build();
    }
}