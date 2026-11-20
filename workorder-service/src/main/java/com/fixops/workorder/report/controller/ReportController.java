package com.fixops.workorder.report.controller;

import com.fixops.workorder.report.dto.ReportSaveDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class ReportController {

    @PostMapping("/report/create")
    public ResponseEntity<?> createReport(@Valid @RequestBody ReportSaveDTO dto) {
        return ResponseEntity.ok("신고 등록 성공");
    }
}
