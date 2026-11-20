package com.fixops.workorder.report.service;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.dto.ReportSaveDTO;
import com.fixops.workorder.report.repository.ReportRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // 유저 신고 등록
    public Report createReport(ReportSaveDTO reportSaveDTO, Long userId) {

        Report report = reportSaveDTO.toEntity(userId);

        return reportRepository.save(report);
    }

    // 유저 신고 조회
    public Page<Report> getMyReport(Long userId, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String[] parts = sort.split(",", -1);

        if (parts.length != 2 || !parts[0].equals("createdAt") ||
                !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Sort.Direction direction = Sort.Direction.fromString(parts[1]);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt").and(Sort.by(direction, "id")));

        return reportRepository.findByReportedBy(userId, pageable);
    }
}
