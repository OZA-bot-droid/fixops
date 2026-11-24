package com.fixops.workorder.workorder.service;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import com.fixops.workorder.report.repository.ReportRepository;
import com.fixops.workorder.workorder.domain.*;
import com.fixops.workorder.workorder.dto.WorkOrderAssignDTO;
import com.fixops.workorder.workorder.dto.WorkOrderHistoryResponseDTO;
import com.fixops.workorder.workorder.dto.WorkOrderResponseDTO;
import com.fixops.workorder.workorder.dto.WorkOrderSaveDTO;
import com.fixops.workorder.workorder.dto.WorkOrderStatusDTO;
import com.fixops.workorder.workorder.repository.WorkOrderAssignmentHistoryRepository;
import com.fixops.workorder.workorder.repository.WorkOrderHistoryRepository;
import com.fixops.workorder.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOrderService {

    private final ReportRepository reportRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderHistoryRepository workOrderHistoryRepository;
    private final WorkOrderAssignmentHistoryRepository workOrderAssignmentHistoryRepository;

    // 작업지시 작성
    public WorkOrderResponseDTO createWorkOrder(WorkOrderSaveDTO dto, Long userId) {

        validateUserId(userId);
        validateSaveFields(dto);

        Report report = null;

        // 신고 기반 등록
        if (dto.getReportId() != null) {
            report = reportRepository.findForWorkOrderConversion(dto.getReportId()).orElseThrow(()
                    -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            validateReportForWorkOrder(report, dto);
        } else {
            // 관리자 직접 등록
            validateDirectWorkOrder(dto);
        }

        validateRelatedWorkOrder(dto.getRelatedWorkOrderId(), dto.getOrganizationId(), dto.getComplexId());

        // TODO: Member - ADMIN 권한 및 단지 접근 검증
        // TODO: Asset - 장소 및 설비 유효성 검증

        WorkOrder workOrder = WorkOrder.builder()
                .organizationId(dto.getOrganizationId())
                .title(dto.getTitle().trim())
                .description(dto.getDescription())
                .complexId(dto.getComplexId())
                .locationId(dto.getLocationId())
                .assetId(dto.getAssetId())
                .reportedBy(userId)
                .priority(dto.getPriority() == null ? WorkOrderPriority.MEDIUM : dto.getPriority())
                .status(WorkOrderStatus.REPORTED)
                .relatedWorkOrderId(dto.getRelatedWorkOrderId())
                .build();

        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);

        // 신고 기반 등록일 때만 원본 신고와 연결
        if (report != null) {
            report.linkWorkOrder(saved.getId());
        }

        // 최초 작업지시 상태 이력 저장
        workOrderHistoryRepository.save(new WorkOrderHistory(saved.getId(), null, WorkOrderStatus.REPORTED, userId));

        return WorkOrderResponseDTO.fromEntity(saved);
    }

    // 조직·단지별 목록 조회
    @Transactional(readOnly = true)
    public Page<WorkOrderResponseDTO> getWorkOrders(Long userId, Long organizationId, Long complexId, int page, int size) {
        validateUserId(userId);
        validateScope(organizationId, complexId);
        validatePage(page, size);

        // TODO: Member 연동 - 요청자의 해당 조직·단지 조회 권한 검증
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return workOrderRepository.findByOrganizationIdAndComplexId(organizationId, complexId, pageable).map(WorkOrderResponseDTO::fromEntity);
    }

    // 작업지시 상세 조회
    @Transactional(readOnly = true)
    public WorkOrderResponseDTO getWorkOrder(Long id, Long userId) {
        validateUserId(userId);
        WorkOrder workOrder = findWorkOrder(id);
        // TODO: Member 연동 - 작업지시 조직·단지 및 본인/담당 작업 조회 권한 검증
        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업자 배정 또는 재배정
    public WorkOrderResponseDTO assignWorkOrder(Long id, WorkOrderAssignDTO dto, Long userId) {
        validateUserId(userId);
        validateAssignFields(dto);
        WorkOrder workOrder = findWorkOrder(id);
        validateVersion(workOrder, dto.getExpectedVersion());
        validateAssignableStatus(workOrder);

        // TODO: Member 연동 - userId의 ADMIN 권한, technicianId의 TECHNICIAN 역할/조직/단지 검증
        Long previousTechnicianId = workOrder.getAssignedTo();

        boolean initialAssign = workOrder.getStatus() == WorkOrderStatus.REPORTED;

        // 연결된 Report 행을 잠그고 ASSIGNED로 상태 동기화
        if (initialAssign) {
            syncReportStatus(workOrder.getId(), WorkOrderStatus.ASSIGNED);
        }

        // 이미 같은 작업자에게 배정되어 있다면 변경 거부
        if (Objects.equals(previousTechnicianId, dto.getTechnicianId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        workOrder.assign(dto.getTechnicianId());

        // 최초 배정: 상태 변경 이력
        if (initialAssign) {
            workOrderHistoryRepository.save(new WorkOrderHistory(workOrder.getId(), WorkOrderStatus.REPORTED, WorkOrderStatus.ASSIGNED, userId));

        }

        // 최초 배정 및 재배정 모두 기록
        workOrderAssignmentHistoryRepository.save(new WorkOrderAssignmentHistory(workOrder.getId(), previousTechnicianId, dto.getTechnicianId(), userId));

        // ASSIGNED 상태 재배정 시 상태는 그대로이고, 상태 변경 이력은 생성하지 않음
        workOrderRepository.flush();


        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업 상태 변경
    public WorkOrderResponseDTO changeWorkOrderStatus(Long id, WorkOrderStatusDTO dto, Long userId) {
        validateUserId(userId);
        validateStatusFields(dto);
        WorkOrder workOrder = findWorkOrder(id);
        validateVersion(workOrder, dto.getExpectedVersion());
        validateStatusTransition(workOrder, dto.getStatus());

        // TODO: Member 연동 - ADMIN/TECHNICIAN/신고자 권한과 해당 작업 배정 관계 확인

        WorkOrderStatus previous = workOrder.getStatus();

        // 연결된 Report 행을 잠그고 상태 동기화
        syncReportStatus(workOrder.getId(), dto.getStatus());

        // WorkOrder 상태 변경
        workOrder.changeStatus(dto.getStatus());

        // 변경 이력 저장
        workOrderHistoryRepository.save(new WorkOrderHistory(workOrder.getId(), previous, dto.getStatus(), userId));

        // SQL 실행 및 낙관적 락 충돌 조기 감지
        workOrderRepository.flush();

        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업 상태 변경 이력 조회
    public List<WorkOrderHistoryResponseDTO> getWorkOrderHistory(Long id, Long userId) {
        validateUserId(userId);
        WorkOrder workOrder = findWorkOrder(id);
        // TODO: Member 연동 - 해당 작업지시 이력 조회 권한 검증
        return workOrderHistoryRepository.findByWorkOrderIdOrderByCreatedAtDescIdDesc(workOrder.getId()).stream().map(WorkOrderHistoryResponseDTO::fromEntity).toList();
    }

    // 상태 동기화 용
    private void syncReportStatus(Long workOrderId, WorkOrderStatus workOrderStatus) {
        // 잠금 조회 메서드인 findByWorkOrderIdForUpdate 사용
        reportRepository.findByWorkOrderIdForUpdate(workOrderId).ifPresent(report -> {

            ReportStatus nextStatus = ReportStatus.valueOf(workOrderStatus.name());

            report.changeStatus(nextStatus);
        });
    }


    //    ==============================
    //
    //        검증 관련
    //
    //    ==============================

    // Gateway에서 확인된 사용자 ID의 필수값만 검증
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    // 관리자 직접 등록 시 필수값 검증
    private void validateDirectWorkOrder(WorkOrderSaveDTO dto) {

        if (dto.getComplexId() == null || dto.getComplexId() <= 0 || dto.getLocationId() == null || dto.getLocationId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (dto.getTitle() == null || dto.getTitle().isBlank() || dto.getTitle().length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // TODO: Member - 관리자의 조직/단지 접근 권한 검증
        // TODO: Asset - locationId가 해당 단지에 속하는지 검증
    }

    // 작업 생성 DTO 기본 검증
    private void validateSaveFields(WorkOrderSaveDTO dto) {

        // 요청 데이터가 없는 경우
        if (dto == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 신고 ID 검증
        // 신고 기반 등록 시 양수 ID 필요
        // 관리자 직접 등록 시 null 허용
        if (dto.getReportId() != null && dto.getReportId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 조직 ID 검증
        // 작업지시는 반드시 특정 조직에 소속되어야 함
        if (dto.getOrganizationId() == null || dto.getOrganizationId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 단지 ID 및 장소 ID 검증
        // 작업지시가 발생한 단지와 장소는 필수 입력값
        if (dto.getComplexId() == null || dto.getComplexId() <= 0
                || dto.getLocationId() == null || dto.getLocationId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 작업지시 제목 검증
        // 제목은 필수이며, 공백만 입력하거나 200자를 초과할 수 없음
        if (dto.getTitle() == null || dto.getTitle().isBlank()
                || dto.getTitle().length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 설비 ID 검증
        // 설비 지정은 선택사항이며, 입력된 경우 양수 ID여야 함
        if (dto.getAssetId() != null && dto.getAssetId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 연관 작업지시 ID 검증
        // 다른 작업지시와 연결하는 것은 선택사항이며,
        // 입력된 경우 양수 ID여야 함
        if (dto.getRelatedWorkOrderId() != null
                && dto.getRelatedWorkOrderId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // TODO: Member 서비스 연동 후 조직 및 단지 접근 권한 검증
        // TODO: Asset 서비스 연동 후 장소 및 설비의 존재 여부 검증
        // TODO: Asset 서비스 연동 후 장소와 설비의 단지 소속 관계 검증
    }

    // 신고 기반 작업지시 등록 검증
    private void validateReportForWorkOrder(Report report, WorkOrderSaveDTO dto) {

        // 접수 상태가 아니거나 이미 작업지시가 연결된 신고는 등록 불가
        if (report.getStatus() != ReportStatus.REPORTED
                || report.getWorkOrderId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        // 원본 신고와 조직 및 발생 위치 일치 여부
        if (!Objects.equals(report.getOrganizationId(), dto.getOrganizationId())
                || !Objects.equals(report.getComplexId(), dto.getComplexId())
                || !Objects.equals(report.getLocationId(), dto.getLocationId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // locationId 변경을 허용하는 경우
        // TODO: Asset 서비스에서 변경된 장소가 해당 단지에 속하는지 검증
    }

    // 같은 DB의 연관 작업 유효성 확인
    private void validateRelatedWorkOrder(Long relatedId, Long organizationId, Long complexId) {
        if (relatedId == null) return;
        WorkOrder related = findWorkOrder(relatedId);
        if (!Objects.equals(related.getOrganizationId(), organizationId) || !Objects.equals(related.getComplexId(), complexId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 조직·단지 검색 조건 기본 검증
    private void validateScope(Long organizationId, Long complexId) {
        if (organizationId == null || organizationId <= 0 || complexId == null || complexId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 페이지 범위 제한
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 작업지시 존재 확인
    private WorkOrder findWorkOrder(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return workOrderRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 작업자 배정 DTO 및 현재 상태 검증
    private void validateAssignFields(WorkOrderAssignDTO dto) {
        if (dto == null || dto.getTechnicianId() == null || dto.getTechnicianId() <= 0 || dto.getExpectedVersion() == null || dto.getExpectedVersion() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateAssignableStatus(WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrderStatus.REPORTED && workOrder.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    // 상태 변경 DTO 검증
    private void validateStatusFields(WorkOrderStatusDTO dto) {
        if (dto == null || dto.getStatus() == null || dto.getExpectedVersion() == null || dto.getExpectedVersion() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // 버전 검사 (실제 동시 경합은 JPA @Version도 검사)
    private void validateVersion(WorkOrder workOrder, Long expectedVersion) {
        if (!Objects.equals(workOrder.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
    }

    // 문서 상태 전이 규칙만 확인 (역할·조직 검증은 향후 연동)
    private void validateStatusTransition(WorkOrder workOrder, WorkOrderStatus next) {
        WorkOrderStatus current = workOrder.getStatus();
        boolean allowed = switch (current) {
            case REPORTED -> next == WorkOrderStatus.REJECTED || next == WorkOrderStatus.CANCELLED;
            case ASSIGNED -> next == WorkOrderStatus.IN_PROGRESS || next == WorkOrderStatus.CANCELLED;
            case IN_PROGRESS -> next == WorkOrderStatus.COMPLETED || next == WorkOrderStatus.CANCELLED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
        if (!allowed) throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        if (next == WorkOrderStatus.IN_PROGRESS && workOrder.getAssignedTo() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}
