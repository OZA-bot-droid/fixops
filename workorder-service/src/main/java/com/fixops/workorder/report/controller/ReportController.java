package com.fixops.workorder.report.controller;

import com.fixops.workorder.common.response.ApiResponse;
import com.fixops.workorder.report.dto.ReportSaveDTO;
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
    public ResponseEntity<?> getMyReport(@RequestHeader("X-Member-Id") Long userId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(defaultValue = "createdAt,desc") String sort){

        return new ResponseEntity<>(ApiResponse.success(reportService.getMyReport(userId, page, size, sort)), HttpStatus.OK);
    }
}
