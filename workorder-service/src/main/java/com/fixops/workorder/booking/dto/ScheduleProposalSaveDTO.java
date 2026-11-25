package com.fixops.workorder.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleProposalSaveDTO { // 시간 재제안 DTO

    // 현재 활성 Proposal ID
    @NotNull
    @Positive
    private Long currentProposalId;


    // 새로 제안할 시작 시각
    @NotNull
    private Instant startAt;


    // 새로 제안할 종료 시각
    @NotNull
    private Instant endAt;


    // 대체 또는 재제안 사유
    @Size(max = 1000)
    private String reason;
}