package com.fixops.workorder.workorder.dto;

import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 작업 상태 변경 dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderStatusDTO {

    // 변경할 작업 상태
    @NotNull(message = "작업 상태는 필수입니다.")
    private WorkOrderStatus status;

    @NotNull(message = "버전은 필수입니다.")
    private Long expectedVersion;
}
