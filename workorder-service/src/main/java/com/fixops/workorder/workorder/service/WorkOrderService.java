package com.fixops.workorder.workorder.service;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.domain.ReportStatus;
import com.fixops.workorder.report.repository.ReportRepository;
import com.fixops.workorder.workorder.domain.*;
import com.fixops.workorder.workorder.dto.*;
import com.fixops.workorder.workorder.repository.WorkOrderAssignmentHistoryRepository;
import com.fixops.workorder.workorder.repository.WorkOrderHistoryRepository;
import com.fixops.workorder.workorder.repository.WorkOrderRepository;
import com.fixops.workorder.booking.service.BookingLifecycleService;
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
    private final BookingLifecycleService bookingLifecycleService;

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
            report.review(userId);
        }

        // 최초 작업지시 상태 이력 저장
        workOrderHistoryRepository.save(new WorkOrderHistory(saved.getId(), null, WorkOrderStatus.REPORTED, userId));

        return WorkOrderResponseDTO.fromEntity(saved);
    }

    // 조직·단지별 목록 조회
    @Transactional(readOnly = true)
    public Page<WorkOrderResponseDTO> getWorkOrders(Long userId, Long organizationId, Long complexId,
                                                    Long locationId, Long assetId, String title, WorkOrderStatus status,
                                                    WorkOrderPriority priority, Long assignedTo, int page, int size) {
        validateUserId(userId);
        validatePage(page, size);

        // 필수 조직 ID 검증
        if (organizationId == null || organizationId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 선택적 검색 ID 검증
        validateOptionalId(complexId);
        validateOptionalId(locationId);
        validateOptionalId(assetId);
        validateOptionalId(assignedTo);

        // 제목 검색어 검증
        String keyword = title == null ? "" : title.trim();

        if (keyword.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // TODO: Member 연동
        // ADMIN 역할 및 조직·단지 접근 권한 검증

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        return workOrderRepository.searchWorkOrders(organizationId,
                                                    complexId,
                                                    locationId,
                                                    assetId,
                                                    status,
                                                    priority,
                                                    assignedTo,
                                                    keyword,
                                                    pageable
        ).map(WorkOrderResponseDTO::fromEntity);
    }

    // 작업지시 상세 조회
    @Transactional(readOnly = true)
    public WorkOrderResponseDTO getWorkOrder(Long id, Long userId) {
        validateUserId(userId);

        WorkOrder workOrder = findWorkOrder(id);

        // TODO: 요청자의 조직·단지 접근 권한 검증
        // TODO: TECHNICIAN의 경우 본인 배정 작업인지 검증

        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업자 배정 또는 재배정
    public WorkOrderResponseDTO assignWorkOrder(Long id, WorkOrderAssignDTO dto, Long userId) {

        // 1. 기본 입력값 검증
        validateUserId(userId);
        validateAssignFields(dto);


        // 2. WorkOrder 비관적 락 조회
        WorkOrder workOrder = findWorkOrderForUpdate(id);


        // 3. 버전 및 배정 가능한 상태 검증
        validateVersion(workOrder, dto.getExpectedVersion());

        validateAssignableStatus(workOrder);


        // TODO: Member 서비스 연동
        // 요청자의 ADMIN 권한 검증
        // 배정 대상 TECHNICIAN 역할 및 조직·단지 검증


        // 4. 기존 담당 기술자 ID
        Long previousTechnicianId = workOrder.getAssignedTo();


        // 5. 최초 배정 여부
        boolean initialAssign = workOrder.getStatus() == WorkOrderStatus.REPORTED;


        // 6. 이미 같은 기술자에게 배정되어 있다면 거부
        if (Objects.equals(previousTechnicianId, dto.getTechnicianId())) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 7. 재배정이라면 기존 활성 예약 자동 취소
        if (!initialAssign) {

            bookingLifecycleService.cancelForReassignment(workOrder, userId);
        }


        // 8. 최초 배정 시 연결된 Report 상태 동기화
        if (initialAssign) {

            syncReportStatus(workOrder.getId(), WorkOrderStatus.ASSIGNED);
        }


        // 9. WorkOrder 담당 기술자 배정 또는 재배정
        workOrder.assign(dto.getTechnicianId());


        // 10. 최초 배정 시 상태 변경 이력 저장
        if (initialAssign) {

            workOrderHistoryRepository.save(new WorkOrderHistory(workOrder.getId(), WorkOrderStatus.REPORTED, WorkOrderStatus.ASSIGNED, userId));
        }


        // 11. 최초 배정 및 재배정 이력 저장
        workOrderAssignmentHistoryRepository.save(new WorkOrderAssignmentHistory(workOrder.getId(), previousTechnicianId, dto.getTechnicianId(), userId));


        // 12. 변경사항 DB 반영 및 버전 충돌 확인
        workOrderRepository.flush();


        // 13. 변경된 작업지시 응답 반환
        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업 상태 변경
    public WorkOrderResponseDTO changeWorkOrderStatus(Long id, WorkOrderStatusDTO dto, Long userId) {

        // 1. 기본 입력값 검증
        validateUserId(userId);
        validateStatusFields(dto);


        // 2. WorkOrder 비관적 락 조회
        WorkOrder workOrder = findWorkOrderForUpdate(id);


        // 3. 버전 검증
        validateVersion(workOrder, dto.getExpectedVersion());


        // 4. 상태 전이 가능 여부 검증
        validateStatusTransition(workOrder, dto.getStatus());


        // TODO: Member 서비스 연동
        // ADMIN / TECHNICIAN / 신고자 권한 검증
        // 해당 작업의 조직·단지 및 배정 관계 검증


        // 5. 기존 상태와 변경할 상태
        WorkOrderStatus previous = workOrder.getStatus();

        WorkOrderStatus next = dto.getStatus();


        // 6. WorkOrder 상태 변경에 따른 Booking 정합성 처리
        switch (next) {

            // 작업지시 취소 - 활성 예약이 존재하면 먼저 예약을 취소
            case CANCELLED -> bookingLifecycleService.cancelForWorkOrderCancellation(workOrder, userId);


            // 작업지시 완료 - 활성 예약이 존재하면 BusinessException을 발생 - > Report와 WorkOrder의 상태 변경 및 이력 저장까지 진행 X
            case COMPLETED -> bookingLifecycleService.validateNoActiveScheduleForCompletion(workOrder);


            // 나머지 상태 변경
            default -> {

                // 예약에 대한 별도 변경 없음
            }
        }


        // 7. 연결된 Report 상태 동기화
        syncReportStatus(workOrder.getId(), next);


        // 8. WorkOrder 상태 변경
        workOrder.changeStatus(next);


        // 9. 상태 변경 이력 저장
        workOrderHistoryRepository.save(new WorkOrderHistory(workOrder.getId(), previous, next, userId));


        // 10. 변경사항 DB 반영 및 버전 충돌 확인
        workOrderRepository.flush();


        // 11. 변경된 작업지시 응답 반환
        return WorkOrderResponseDTO.fromEntity(workOrder);
    }

    // 작업 상태 변경 이력 조회
    @Transactional(readOnly = true)
    public List<WorkOrderHistoryResponseDTO> getWorkOrderHistory(Long id, Long userId) {
        validateUserId(userId);
        WorkOrder workOrder = findWorkOrder(id);
        // TODO: Member 연동 - 해당 작업지시 이력 조회 권한 검증
        return workOrderHistoryRepository.findByWorkOrderIdOrderByCreatedAtAscIdAsc(workOrder.getId()).stream().map(WorkOrderHistoryResponseDTO::fromEntity).toList();
    }

    // 상태 동기화 용
    private void syncReportStatus(Long workOrderId, WorkOrderStatus workOrderStatus) {
        // 잠금 조회 메서드인 findByWorkOrderIdForUpdate 사용
        reportRepository.findByWorkOrderIdForUpdate(workOrderId).ifPresent(report -> {

            ReportStatus nextStatus = ReportStatus.valueOf(workOrderStatus.name());

            report.changeStatus(nextStatus);
        });
    }


    // 작업자 배정·재배정 이력 조회
    @Transactional(readOnly = true)
    public List<WorkOrderAssignmentHistoryResponseDTO> getAssignmentHistory(Long id, Long userId) {

        validateUserId(userId);

        WorkOrder workOrder = findWorkOrder(id);

        // TODO: ADMIN 및 해당 작업 조회 권한 검증

        return workOrderAssignmentHistoryRepository.findByWorkOrderIdOrderByCreatedAtDescIdDesc(workOrder.getId())
                                                    .stream().map(WorkOrderAssignmentHistoryResponseDTO::fromEntity).toList();
    }

    // 작업자(TECHNICIAN) 본인에게 배정된 작업 조회
    @Transactional(readOnly = true)
    public Page<WorkOrderResponseDTO> getMyWorkOrders(Long userId, Long organizationId, Long complexId, Long locationId,
                                                      Long assetId, String title, WorkOrderStatus status, WorkOrderPriority priority, int page, int size) {
        // 사용자 ID 검증
        validateUserId(userId);

        // 페이지 검증
        validatePage(page, size);

        // 필수 조직 ID 검증
        if (organizationId == null || organizationId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 선택적 ID 검증
        validateOptionalId(complexId);
        validateOptionalId(locationId);
        validateOptionalId(assetId);

        // 제목 검색어 처리
        String keyword = title == null ? "" : title.trim();

        if (keyword.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // TODO: Member 서비스 연동
        // 1. TECHNICIAN 역할 검증
        // 2. 조직 소속 검증
        // 3. 승인된 단지 접근 범위 검증

        // 최신순 조회
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        // assignedTo에 작업자 ID 전달해서 작업자 본인의 작업 목록 조회
        return workOrderRepository.searchWorkOrders(organizationId, complexId, locationId, assetId, status, priority, userId,
                keyword, pageable).map(WorkOrderResponseDTO::fromEntity);
    }


    // 작업지시 상태 변경용 비관적 락 조회
    private WorkOrder findWorkOrderForUpdate(Long id) {

        // 작업지시 ID 유효성 검증
        if (id == null || id <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // WorkOrder 행에 비관적 쓰기 잠금 획득
        return workOrderRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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

        // TODO: Member - 관리자의 조직/단지 접근 권한 검증
        // TODO: Asset - locationId가 해당 단지에 속하는지 검증




    }

    // 선택적인 ID 검증
    private void validateOptionalId(Long id) {
        if (id != null && id <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT
            );
        }
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
