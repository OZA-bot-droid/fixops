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
public class ScheduleSaveDTO { // 최초 예약 요청 DTO

    // 작업지시 ID
    @NotNull
    @Positive
    private Long workOrderId;


    // 희망 방문 시작 시각
    @NotNull
    private Instant startAt;


    // 희망 방문 종료 시각
    @NotNull
    private Instant endAt;


    // 최초 시간 제안 사유 또는 요청사항
    @Size(max = 1000)
    private String reason;
}