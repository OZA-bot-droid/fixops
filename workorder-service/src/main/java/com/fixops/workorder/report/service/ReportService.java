package com.fixops.workorder.report.service;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import com.fixops.workorder.report.dto.ReportRejectDTO;
import com.fixops.workorder.report.dto.ReportResponseDTO;
import com.fixops.workorder.report.dto.ReportSaveDTO;
import com.fixops.workorder.report.dto.ReportUpdateDTO;
import com.fixops.workorder.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public ReportResponseDTO createReport(ReportSaveDTO reportSaveDTO, Long userId) {

        // dto 값 검증 및 권한 검증
        validateCreateReport(reportSaveDTO, userId);

        Report report = reportSaveDTO.toEntity(userId);

        Report saved = reportRepository.save(report);

        return ReportResponseDTO.fromEntity(saved);
    }

    // 유저 신고 조회
    @Transactional(readOnly = true)
    public Page<Report> getMyReport(Long userId, String title, String status, int page, int size) {
        validateUserId(userId);

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
        ReportStatus reportStatus = parseReportStatus(status);

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
    public ReportResponseDTO updateReport(Long reportId, ReportUpdateDTO dto, Long userId) {
        // 1. 사용자 및 신고 ID 검증
        validateUserId(userId);
        validateId(reportId);

        // 2. DTO null 검증
        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 3. DTO 입력값 검증
        validateReportFields(dto.getLocationId(), dto.getTitle());

        // 4. 수정 가능한 신고인지 확인 및 비관적 락 획득
        Report report = validateEditableReport(reportId, userId);

        // 5. 장소 소속 검증
        validateReportLocation(report.getOrganizationId(), report.getComplexId(), dto.getLocationId(), userId);

        // 6. 신고 수정
        report.updateReport(dto.getLocationId(), dto.getTitle(), dto.getDescription());

        // 7. 변경사항 DB 반영
        reportRepository.flush();

        // 8. DTO 반환
        return ReportResponseDTO.fromEntity(report);
    }

    // 신고 취소
    public ReportResponseDTO cancelReport(Long reportId, Long userId) {
        Report report = validateEditableReport(reportId, userId);

        report.changeStatus(ReportStatus.CANCELLED);

        // flush 호출 - DTO로 변환하는 시점에 최신 updatedAt 값을 포함하기 위해 사용.
        reportRepository.flush();

        return ReportResponseDTO.fromEntity(report);
    }

    // 관리자 신고 목록 조회
    @Transactional(readOnly = true)
    public Page<ReportResponseDTO> getAdminReports(Long userId, Long organizationId, Long complexId,
                                                   Long locationId, String title, String status,
                                                   int page, int size) {
        // 사용자 및 필수 조직 ID 검증
        validateUserId(userId);
        validateId(organizationId);

        // 선택적 검색 조건 검증
        if (complexId != null) {
            validateId(complexId);
        }

        if (locationId != null) {
            validateId(locationId);
        }

        // 페이지 검증
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 제목 검색어 처리
        String keyword = title == null ? "" : title.trim();

        if (keyword.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 상태값 변환
        ReportStatus reportStatus = parseReportStatus(status);

        // TODO: Member 서비스 연동
        // 1. 관리자 역할 확인
        // 2. 요청 조직에 소속되어 있는지 확인
        // 3. 접근 가능한 단지 범위 확인

        // TODO: Asset 서비스 연동
        // 요청 위치의 조직·단지 소속 검증

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        return reportRepository.findAdminReports(organizationId, complexId, locationId, reportStatus, keyword, pageable).map(ReportResponseDTO::fromEntity);
    }

    // 신고 상세 조회
    @Transactional(readOnly = true)
    public ReportResponseDTO getMyReportDetail(Long reportId, Long userId) {
        validateUserId(userId);
        validateId(reportId);

        Report report = reportRepository.findByIdAndReportedBy(reportId, userId).orElseThrow(()
                -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return ReportResponseDTO.fromEntity(report);
    }

    // 신고 반려
    public ReportResponseDTO rejectReport(Long reportId, ReportRejectDTO dto, Long userId) {
        validateUserId(userId);
        validateId(reportId);

        if (dto == null || dto.getReason() == null || dto.getReason().isBlank() || dto.getReason().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Report report = reportRepository.findForWorkOrderConversion(reportId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // TODO: ADMIN 및 조직·단지 권한 검증

        if (report.getStatus() != ReportStatus.REPORTED || report.getWorkOrderId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        report.reject(userId, dto.getReason().trim());

        reportRepository.flush();

        return ReportResponseDTO.fromEntity(report);
    }

    // 관리자 신고 상세 조회
    @Transactional(readOnly = true)
    public ReportResponseDTO getAdminReportDetail(Long reportId, Long userId) {
        validateUserId(userId);
        validateId(reportId);

        Report report = reportRepository.findById(reportId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // TODO: Member 연동
        // TODO: ADMIN 역할 검증
        // TODO: 해당 조직 소속 검증
        // report.getOrganizationId() 기준 조직 접근 권한 검증
        // report.getComplexId() 기준 단지 접근 권한 검증

        return ReportResponseDTO.fromEntity(report);
    }

//    ==============================
//
//        검증 관련 및 공통
//
//    ==============================


    // 신고 상태 문자열을 ReportStatus Enum으로 변환
    private ReportStatus parseReportStatus(String status) {

        // null인 경우 빈 문자열로 변환하고 앞뒤 공백 제거
        String statusValue = status == null ? "" : status.trim();

        // 상태값이 없거나 ALL이면 전체 조회
        if (statusValue.isEmpty() || statusValue.equalsIgnoreCase("ALL")) {
            return null;
        }

        // 문자열을 ReportStatus Enum으로 변환
        try {
            return ReportStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 사용자 ID 검증
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    // 리소스 ID 검증
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 신고 dto 값 검증
    private void validateReportFields(Long locationId, String title) {
        // 장소 ID 필수
        validateId(locationId);

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
        validateUserId(userId);

        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        validateId(dto.getOrganizationId());
        validateId(dto.getComplexId());


        validateReportFields(dto.getLocationId(), dto.getTitle());

        validateMember(userId, dto.getOrganizationId(), dto.getComplexId());

        validateReportLocation(dto.getOrganizationId(), dto.getComplexId(), dto.getLocationId(), userId);

        // TODO: 인증된 사용자 조직/세대 권한 검증
    }

    // 수정·취소 가능한 신고인지 조회
    private Report validateEditableReport(Long reportId, Long userId) {

        // 사용자 ID 검사
        validateUserId(userId);

        validateId(reportId);

        // 본인 신고 조회
        Report report = reportRepository.findEditableByIdAndReportedByForUpdate(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 작업지시로 전환된 신고는 수정·취소 불가
        if (report.getWorkOrderId() != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION
            );
        }

        // REPORTED 상태에서만 수정·취소 가능
        if (report.getStatus() != ReportStatus.REPORTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        return report;
    }


}
