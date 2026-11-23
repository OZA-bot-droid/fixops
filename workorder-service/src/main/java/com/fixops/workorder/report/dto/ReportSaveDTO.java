package com.fixops.workorder.report.dto;

import com.fixops.workorder.report.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSaveDTO {
    // 조직 ID
    @NotNull(message = "조직 ID는 필수입니다.")
    private Long organizationId;

    // 건물(단지) ID
    @NotNull(message = "단지 ID는 필수입니다.")
    private Long complexId;

    // 장소 ID
    @NotNull(message = "장소는 필수입니다.")
    private Long locationId;

    // 신고 제목
    @NotBlank(message = "신고 제목은 필수입니다.")
    @Size(max = 200, message = "신고 제목은 200자 이하여야 합니다.")
    private String title;

    // 신고 내용
    private String description;

    public Report toEntity(Long reportedBy) {
        return Report.builder()
                .organizationId(this.organizationId)
                .complexId(this.complexId)
                .locationId(this.locationId)
                .title(this.title.trim())
                .description(this.description)
                .reportedBy(reportedBy)
                .build();
    }
}
