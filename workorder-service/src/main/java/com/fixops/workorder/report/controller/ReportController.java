package com.fixops.workorder.report.controller;

import com.fixops.workorder.common.response.ApiResponse;
import com.fixops.workorder.report.dto.ReportRejectDTO;
import com.fixops.workorder.report.dto.ReportResponseDTO;
import com.fixops.workorder.report.dto.ReportSaveDTO;
import com.fixops.workorder.report.dto.ReportUpdateDTO;
import com.fixops.workorder.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    // 유저 신고 등록
    @PostMapping("/report/create")
    public ResponseEntity<?> createReport(@Valid @RequestBody ReportSaveDTO dto, @RequestHeader("X-Member-Id") Long userId) {

        return new ResponseEntity<>(ApiResponse.success(reportService.createReport(dto, userId)), HttpStatus.CREATED);
    }

    // 유저 신고 조회
    @GetMapping("/report/my")
    public ResponseEntity<?> getMyReport(@RequestHeader("X-Member-Id") Long userId, @RequestParam(required = false) String title,
                                         @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.success(reportService.getMyReport(userId, title, status, page, size)));
    }

    // 본인 신고 상세 조회
    @GetMapping("/report/my/{reportId}")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> getMyReportDetail(@PathVariable Long reportId, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getMyReportDetail(reportId, userId)));
    }


    // 신고 수정
    @PatchMapping("/report/{reportId}")
    public ResponseEntity<?> updateReport(@PathVariable Long reportId, @Valid @RequestBody ReportUpdateDTO dto, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.updateReport(reportId, dto, userId)));
    }

    // 신고 취소
    @DeleteMapping("/report/{reportId}")
    public ResponseEntity<?> deleteReport(@PathVariable Long reportId, @RequestHeader("X-Member-Id") Long userId) {

        return ResponseEntity.ok(ApiResponse.success(reportService.cancelReport(reportId, userId)));
    }

    // 관리자 - 조직 전체 및 필터별 신고 목록 조회
    @GetMapping("/report")
    public ResponseEntity<?> getAdminReports(@RequestHeader("X-Member-Id") Long userId, @RequestParam Long organizationId,
                                             @RequestParam(required = false) Long complexId, @RequestParam(required = false) Long locationId,
                                             @RequestParam(required = false) String title, @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getAdminReports(userId, organizationId, complexId, locationId, title, status, page, size)));
    }

    // 관리자 - 신고 상세 조회
    @GetMapping("/report/{reportId}")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> getAdminReportDetail(@PathVariable Long reportId, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getAdminReportDetail(reportId, userId)));
    }

    // 관리자 - 신고 반려
    @PatchMapping("/report/{reportId}/reject")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> rejectReport(@PathVariable Long reportId, @Valid @RequestBody ReportRejectDTO dto,
                                                                       @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.rejectReport(reportId, dto, userId)));
    }
}
