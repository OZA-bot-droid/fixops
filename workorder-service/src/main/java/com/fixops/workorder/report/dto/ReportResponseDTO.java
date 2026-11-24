package com.fixops.workorder.report.dto;


import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDTO {

    private Long id;

    // 조직 id
    private Long organizationId;

    // 건물(단지) ID
    private Long complexId;

    // 장소 ID
    private Long locationId;

    // 제목
    private String title;

    // 상세 내용
    private String description;

    // 신고자(user) id
    private Long reportedBy;

    // 신고 상태
    private ReportStatus status;

    // 연동된 작업지시 id
    private Long workOrderId;

    // 신고를 검토한 관리자 ID
    private Long reviewedBy;

    // 관리자 검토 일시
    private Instant reviewedAt;

    // 거절 사유
    private String rejectionReason;

    private Instant createdAt;
    private Instant updatedAt;

    public static ReportResponseDTO fromEntity(Report report) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .organizationId(report.getOrganizationId())
                .complexId(report.getComplexId())
                .locationId(report.getLocationId())
                .title(report.getTitle())
                .description(report.getDescription())
                .reportedBy(report.getReportedBy())
                .status(report.getStatus())
                .workOrderId(report.getWorkOrderId())
                .reviewedBy(report.getReviewedBy())
                .reviewedAt(report.getReviewedAt())
                .rejectionReason(report.getRejectionReason())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}