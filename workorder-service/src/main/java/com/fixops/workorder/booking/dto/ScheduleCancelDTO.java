package com.fixops.workorder.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleCancelDTO { // 예약 취소 DTO

    // 취소 사유
    @NotBlank
    @Size(max = 1000)
    private String reason;
}