package com.fixops.workorder.report.service;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import com.fixops.workorder.report.dto.ReportSaveDTO;
import com.fixops.workorder.report.dto.ReportUpdateDTO;
import com.fixops.workorder.report.repository.ReportRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Locale;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // 유저 신고 등록
    public Report createReport(ReportSaveDTO reportSaveDTO, Long userId) {

        // dto 값 검증 및 권한 검증
        validateCreateReport(reportSaveDTO, userId);


        Report report = reportSaveDTO.toEntity(userId);

        return reportRepository.save(report);
    }

    // 유저 신고 조회
    public Page<Report> getMyReport(Long userId, String title, String status, int page, int size) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        // 1. 페이지 검증
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 2. 생성일 기준 내림차순(최신순) 정렬
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        // 3. 제목 검색어 처리
        String keyword = title == null ? "" : title.trim();

        if (keyword.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        boolean hasTitle = !keyword.isEmpty();

        // 4. 상태 필터 처리
        ReportStatus reportStatus = null;

        // 공백 제거
        String statusValue = status == null ? "" : status.trim();

        // 상태값이 비어 있지 않고, 전체 조회(ALL) 아닐 때, enum으로 변환
        if (!statusValue.isEmpty() && !statusValue.equalsIgnoreCase("ALL")) {
            try {
                // 입력된 문자열을 대문자로 변환하고 이름이 일치하는 enum 상수를 찾아 반환
                // Locale.ROOT는 사용자의 언어·지역 설정에 영향을 받지 않는 방식으로 대소문자를 변환하기 위해 사용
                reportStatus = ReportStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }

        boolean hasStatus = reportStatus != null;

        // 조건별 분기
        // 5. 제목 + 상태
        if (hasTitle && hasStatus) {
            return reportRepository.findByReportedByAndTitleContainingIgnoreCaseAndStatus(userId, keyword, reportStatus, pageable);
        }

        // 6. 제목만
        if (hasTitle) {
            return reportRepository.findByReportedByAndTitleContainingIgnoreCase(userId, keyword, pageable);
        }

        // 7. 상태만
        if (hasStatus) {
            return reportRepository.findByReportedByAndStatus(userId, reportStatus, pageable);
        }

        // 8. 조건 없음 - 전체 조회
        return reportRepository.findByReportedBy(userId, pageable);
    }

    // 신고 수정
    public Report updateReport(Long reportId, ReportUpdateDTO dto, Long userId) {
        // 본인 신고 + REPORTED 상태 검사
        Report report = validateEditableReport(reportId, userId);

        // 회원·조직·단지 검증
        validateMember(userId, report.getOrganizationId(), report.getComplexId());

        // 수정할 DTO 검증
        validateReportFields(dto.getLocationId(), dto.getTitle());

        // 장소 검증
        validateReportLocation(report.getOrganizationId(), report.getComplexId(), dto.getLocationId(), userId);

        report.updateReport(dto.getLocationId(), dto.getTitle(), dto.getDescription());

        return report;
    }

    // 신고 취소
    public Report cancelReport(Long reportId, Long userId) {

        Report report = validateEditableReport(reportId, userId);

        report.changeStatus(ReportStatus.CANCELLED);

        return report;
    }


//    ==============================
//
//        검증 함수
//
//    ==============================

    // 신고 dto 값 검증
    private void validateReportFields(Long locationId, String title) {
        // 장소 ID 필수
        if (locationId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 제목 필수 및 최대 200자
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 신고 장소 검증
    private void validateReportLocation(Long organizationId, Long complexId, Long locationId, Long userId) {
        // 현재 구현 가능한 필수값 검증
        if (organizationId == null || complexId == null || locationId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // TODO: Asset / Member 서비스 연동 후 추가
        // - locationId가 실제 존재하는지
        // - 해당 장소가 complexId에 속하는지
        // - organizationId의 관리 범위에 속하는지
        // - 현재 활성화된 장소인지
        // - userId에게 신고 권한이 있는지
    }

    // TODO: Member 서비스 연동 후 구현
    // 조직·회원 검증
    private void validateMember(Long userId, Long organizationId, Long complexId) {
        // 필수값 검사
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        if (organizationId == null || complexId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 1. 인증된 사용자 확인
        // 2. 조직 소속 및 역할 검증
        // 3. 단지 접근 권한 검증
        // 4. 신고 가능한 세대 또는 공용 공간인지 검증
    }

    // 신고 생성 검증
    private void validateCreateReport(ReportSaveDTO dto, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        validateReportFields(dto.getLocationId(), dto.getTitle());

        validateMember(userId, dto.getOrganizationId(), dto.getComplexId());

        validateReportLocation(dto.getOrganizationId(), dto.getComplexId(), dto.getLocationId(), userId);

        // TODO: 인증된 사용자 조직/세대 권한 검증
    }

    // 수정·취소 가능한 신고인지 조회
    private Report validateEditableReport(Long reportId, Long userId) {

        // 사용자 ID 검사
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        // 본인 신고 조회
        Report report = reportRepository.findByIdAndReportedBy(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // REPORTED 상태에서만 수정·취소 가능
        if (report.getStatus() != ReportStatus.REPORTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        return report;
    }


}
