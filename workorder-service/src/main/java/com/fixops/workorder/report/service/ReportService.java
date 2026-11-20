package com.fixops.workorder.report.service;

import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.dto.ReportSaveDTO;
import com.fixops.workorder.report.repository.ReportRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // 유저 신고 등록
    public Long createReport(ReportSaveDTO reportSaveDTO, Long userId){

        Report report =  reportSaveDTO.toEntity(userId);

        reportRepository.save(report);
        log.info("새로운 신고가 정상적으로 등록되었습니다.");

        return report.getId();
    }
}
