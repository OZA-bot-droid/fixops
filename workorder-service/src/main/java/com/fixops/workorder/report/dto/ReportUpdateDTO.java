package com.fixops.workorder.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportUpdateDTO {

    // 신고 장소 ID
    @NotNull(message = "장소는 필수입니다.")
    @Positive(message = "장소 ID는 양수여야 합니다.")
    private Long locationId;

    @NotBlank(message = "신고 제목은 필수입니다.")
    @Size(max = 200, message = "신고 제목은 200자 이하여야 합니다.")
    private String title;

    private String description;
}