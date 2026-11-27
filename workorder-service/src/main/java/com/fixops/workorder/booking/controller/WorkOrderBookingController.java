package com.fixops.workorder.booking.controller;

import com.fixops.workorder.booking.dto.ReportBookingContextDTO;
import com.fixops.workorder.booking.dto.ScheduleReadDTO;
import com.fixops.workorder.booking.service.ScheduleQueryService;
import com.fixops.workorder.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class WorkOrderBookingController {

    private final ScheduleQueryService scheduleQueryService;

    // 특정 WorkOrder의 전체 방문 예약 이력 조회
    @GetMapping("/{workOrderId}/schedules")
    public ResponseEntity<ApiResponse<List<ScheduleReadDTO>>> getWorkOrderSchedules(@RequestHeader("X-Member-Id") Long userId, @PathVariable Long workOrderId) {

        List<ScheduleReadDTO> response = scheduleQueryService.getWorkOrderSchedules(workOrderId, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    // 입주민 본인 신고의 방문 예약 가능 여부 조회
    @GetMapping("/report/my/{reportId}/booking-context")
    public ResponseEntity<ApiResponse<ReportBookingContextDTO>> getReportBookingContext(@RequestHeader("X-Member-Id") Long userId, @PathVariable Long reportId) {

        ReportBookingContextDTO response = scheduleQueryService.getReportBookingContext(reportId, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}