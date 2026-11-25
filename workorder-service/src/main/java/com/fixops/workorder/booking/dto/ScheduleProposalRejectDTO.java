package com.fixops.workorder.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleProposalRejectDTO { // 제안 거절 DTO

    // 거절 사유
    @NotBlank
    @Size(max = 1000)
    private String reason;
}