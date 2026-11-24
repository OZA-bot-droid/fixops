package com.fixops.workorder.workorder.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 작업자 배정·재배정 dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAssignDTO {

    // 배정할 작업자 ID
    @NotNull(message = "작업자 ID는 필수입니다.")
    @Positive
    private Long technicianId;

    // 동시 상태 변경 방지를 위한 현재 작업지시 버전
    // 0은 허용하지만 음수는 허용X
    @NotNull(message = "버전은 필수입니다.")
    @PositiveOrZero
    private Long expectedVersion;
}
