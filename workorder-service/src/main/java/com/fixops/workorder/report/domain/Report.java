package com.fixops.workorder.report.domain;


import com.fixops.workorder.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "report")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BaseEntity {

    // 신고 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 조직 ID
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // 신고 제목
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 건물(단지) ID
    @Column(name = "complex_id", nullable = false)
    private Long complexId;

    // 장소 ID
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    // 신고 내용
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 신고자 ID
    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    // 신고 처리 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status = ReportStatus.REPORTED;

    // 신고를 검토한 관리자 ID
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    // 관리자 검토 일시
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // 거절 사유
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // 작업지시로 전환되었을 때 생성된 WorkOrder ID
    @Column(name = "work_order_id")
    private Long workOrderId;


    // 신고 상태 변경
    public void changeStatus(ReportStatus newStatus) {
        this.status = newStatus;
    }

    // 신고 수정
    public void updateReport(Long locationId, String title, String description) {
        this.locationId = locationId;
        this.title = title.trim();
        this.description = description;
    }

    // 신고와 작업지시 연결
    public void linkWorkOrder(Long workOrderId) {
        this.workOrderId = workOrderId;
    }
}
