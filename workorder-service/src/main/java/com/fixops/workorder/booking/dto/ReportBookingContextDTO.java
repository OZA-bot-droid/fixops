package com.fixops.workorder.booking.dto;

import com.fixops.workorder.report.domain.ReportStatus;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;


// 입주민의 신고 상세 화면에서 방문 예약 가능 여부를 판단 dto
public record ReportBookingContextDTO(

        Long reportId,

        ReportStatus reportStatus,

        Long workOrderId,

        WorkOrderStatus workOrderStatus,

        Long technicianId,

        // 현재 활성 예약 존재 여부
        boolean hasActiveSchedule,

        // 해당 사용자가 직접 조회 가능한 경우에만 반환
        Long activeScheduleId,

        // 신규 방문 예약 요청 가능 여부
        boolean canRequestBooking,

        // 예약 요청 불가 사유
        String unavailableReason

) {
}