package com.fixops.workorder.workorder.dto;

import com.fixops.workorder.workorder.domain.WorkOrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


// 작업지시 생성 dto
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderSaveDTO {

    // 신고 ID (관리자 직접 등록 시 null)
    @Positive
    private Long reportId;

    // 조직 ID
    @NotNull
    @Positive
    private Long organizationId;

    // 작업지시 제목
    @NotBlank
    @Size(max = 200)
    private String title;

    // 작업지시 내용
    private String description;

    // 단지 ID
    @NotNull
    @Positive
    private Long complexId;

    // 장소 ID
    @NotNull
    @Positive
    private Long locationId;

    // 설비 ID
    @Positive
    private Long assetId;

    // 우선순위
    private WorkOrderPriority priority;

    // 연관 작업지시 ID
    @Positive
    private Long relatedWorkOrderId;
}
