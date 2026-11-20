package com.fixops.workorder.report.controller;

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

    @PostMapping("/report/create")
    public ResponseEntity<?> createReport(@Valid @RequestBody ReportSaveDTO dto, @RequestHeader("X-Member-Id") Long userId) {

        return new ResponseEntity<>(reportService.createReport(dto, userId), HttpStatus.CREATED);
    }
}
