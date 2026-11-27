package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.dto.*;
import com.fixops.workorder.booking.policy.BookingAccessPolicy;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import com.fixops.workorder.booking.repository.ScheduleProposalRepository;
import com.fixops.workorder.booking.repository.ScheduleSpecifications;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;

import com.fixops.workorder.report.domain.Report;
import com.fixops.workorder.report.repository.ReportRepository;

import com.fixops.workorder.workorder.domain.WorkOrder;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import com.fixops.workorder.workorder.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
// REPEATABLE_READ를 사용하면 하나의 조회 트랜잭션에서 동일한 스냅샷을 기준으로 데이터를 조회
public class ScheduleQueryService {

    private final MaintenanceScheduleRepository scheduleRepository;

    private final ScheduleProposalRepository proposalRepository;

    private final WorkOrderRepository workOrderRepository;

    private final ReportRepository reportRepository;

    private final BookingAccessPolicy accessPolicy;

    private final Clock clock;


    // =====================================
    // 1. 관리자 예약 목록
    // =====================================

    public Page<ScheduleReadDTO> getSchedules(ScheduleSearchDTO dto, Long userId) {

        accessPolicy.validateActor(userId);

        Pageable pageable = validateSearch(dto);


        // TODO: Member 서비스 연동
        // 실제 ADMIN 역할 및 관리 가능한 조직·단지 검증


        // 현재는 자신이 생성한 WorkOrder의 예약만 조회
        Page<MaintenanceSchedule> schedules = scheduleRepository.findAll(ScheduleSpecifications.search(dto, userId, dto.getTechnicianId(), null), pageable);


        return mapPage(schedules, userId);
    }


    // =====================================
    // 2. 기술자 본인의 예약 목록
    // =====================================

    public Page<ScheduleReadDTO> getMySchedules(ScheduleSearchDTO dto, Long userId) {

        accessPolicy.validateActor(userId);

        Pageable pageable = validateSearch(dto);


        // 다른 기술자 ID를 전달하는 방식 차단
        if (dto.getTechnicianId() != null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // TODO: Member 서비스 연동
        // TECHNICIAN 역할 및 조직 접근 권한 검증


        Page<MaintenanceSchedule> schedules = scheduleRepository.findAll(ScheduleSpecifications.search(dto, null, userId, null), pageable);


        return mapPage(schedules, userId);
    }


    // =====================================
    // 3. 요청자 본인의 예약 목록
    // =====================================

    public Page<ScheduleReadDTO> getRequestedSchedules(ScheduleSearchDTO dto, Long userId) {

        accessPolicy.validateActor(userId);

        Pageable pageable = validateSearch(dto);


        // 요청자용 목록에서는 기술자 ID 검색 미지원
        if (dto.getTechnicianId() != null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // TODO: Member 서비스 연동
        // 실제 조직 및 사용자 접근 권한 검증


        Page<MaintenanceSchedule> schedules = scheduleRepository.findAll(ScheduleSpecifications.search(dto, null, null, userId), pageable);


        return mapPage(schedules, userId);
    }


    // =====================================
    // 4. 예약 상세 조회
    // =====================================

    public ScheduleReadDTO getSchedule(Long scheduleId, Long userId) {

        accessPolicy.validateActor(userId);


        MaintenanceSchedule schedule = findSchedule(scheduleId);


        WorkOrder workOrder = findWorkOrder(schedule.getWorkOrderId());


        accessPolicy.validateViewSchedule(workOrder, schedule, userId);


        ScheduleProposal proposal = findCurrentProposal(schedule);


        return toReadDTO(schedule, workOrder, proposal, userId, clock.instant());
    }


    // =====================================
    // 5. 전체 일정 협의 이력 조회
    // =====================================

    public List<ScheduleProposalResponseDTO> getProposals(Long scheduleId, Long userId) {

        accessPolicy.validateActor(userId);


        MaintenanceSchedule schedule = findSchedule(scheduleId);


        WorkOrder workOrder = findWorkOrder(schedule.getWorkOrderId());


        accessPolicy.validateViewSchedule(workOrder, schedule, userId);


        return proposalRepository.findByScheduleIdOrderByCreatedAtAscIdAsc(scheduleId).stream().map(ScheduleProposalResponseDTO::fromEntity).toList();
    }


    // =====================================
    // 6. WorkOrder별 전체 예약 이력 조회
    // =====================================

    public List<ScheduleReadDTO> getWorkOrderSchedules(Long workOrderId, Long userId) {

        accessPolicy.validateActor(userId);


        WorkOrder workOrder = findWorkOrder(workOrderId);


        accessPolicy.validateViewWorkOrderSchedules(workOrder, userId);


        List<MaintenanceSchedule> schedules = scheduleRepository.findByWorkOrderIdOrderByCreatedAtDescIdDesc(workOrderId);


        // 현재 Proposal 일괄 조회
        Map<Long, ScheduleProposal> proposals = loadProposals(schedules);


        Instant now = clock.instant();


        return schedules.stream().map(schedule -> toReadDTO(schedule, workOrder, getCurrentProposal(schedule, proposals), userId, now)).toList();
    }


    // =====================================
    // 7. 입주민 신고 기반 예약 가능 여부 조회
    // =====================================

    public ReportBookingContextDTO getReportBookingContext(Long reportId, Long userId) {

        accessPolicy.validateActor(userId);

        validateRequiredId(reportId);


        // 본인이 등록한 신고만 조회
        Report report = reportRepository.findByIdAndReportedBy(reportId, userId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));


        // 1. WorkOrder가 아직 생성되지 않은 경우
        if (report.getWorkOrderId() == null) {

            return new ReportBookingContextDTO(report.getId(), report.getStatus(), null, null, null, false, null, false, "연결된 작업지시가 없습니다.");
        }


        // 2. 연결된 WorkOrder 조회
        WorkOrder workOrder = findWorkOrder(report.getWorkOrderId());


        // 신고와 작업지시의 조직·단지 일치 확인
        if (!Objects.equals(report.getOrganizationId(), workOrder.getOrganizationId()) || !Objects.equals(report.getComplexId(), workOrder.getComplexId())) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }


        // 3. 해당 WorkOrder의 활성 예약 조회
        Optional<MaintenanceSchedule> activeSchedule = scheduleRepository.findByWorkOrderIdAndStatusIn(workOrder.getId(), MaintenanceScheduleStatus.activeStatuses());


        boolean hasActiveSchedule = activeSchedule.isPresent();


        // 4. 현재 사용자가 조회할 수 있는 예약 ID만 반환
        Long activeScheduleId = activeSchedule.filter(schedule -> accessPolicy.canViewSchedule(workOrder, schedule, userId)).map(MaintenanceSchedule::getId).orElse(null);


        // 5. 신규 방문 예약 가능 여부 판단
        String unavailableReason = null;


        if (hasActiveSchedule) {

            unavailableReason = "이미 진행 중인 방문 예약이 존재합니다.";

        } else if (workOrder.getStatus() == WorkOrderStatus.REPORTED) {

            unavailableReason = "담당 기술자 배정을 기다리고 있습니다.";

        } else if (!isBookableWorkOrder(workOrder)) {

            unavailableReason = "현재 작업 상태에서는 방문 예약을 요청할 수 없습니다.";

        } else if (workOrder.getAssignedTo() == null || workOrder.getAssignedTo() <= 0) {

            unavailableReason = "배정된 담당 기술자가 없습니다.";

        } else if (Objects.equals(workOrder.getAssignedTo(), userId)) {

            unavailableReason = "본인에게 방문 예약을 요청할 수 없습니다.";
        }


        boolean canRequestBooking = unavailableReason == null;


        // 6. 신고 및 예약 관련 최소 정보 반환
        return new ReportBookingContextDTO(

                report.getId(),

                report.getStatus(),

                workOrder.getId(),

                workOrder.getStatus(),

                workOrder.getAssignedTo(),

                hasActiveSchedule,

                activeScheduleId,

                canRequestBooking,

                unavailableReason

        );
    }

    // =====================================
    // 8. 검색 조건 및 페이지네이션 검증
    // =====================================

    private Pageable validateSearch(ScheduleSearchDTO dto) {

        if (dto == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 필수 조직 ID
        validateRequiredId(dto.getOrganizationId());


        // 선택적 검색 ID
        validateOptionalId(dto.getComplexId());
        validateOptionalId(dto.getLocationId());
        validateOptionalId(dto.getAssetId());
        validateOptionalId(dto.getTechnicianId());


        // 페이지 범위 검증
        if (dto.getPage() < 0 || dto.getSize() < 1 || dto.getSize() > 100) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 시작 시각과 종료 시각이 모두 존재하는 경우
        // 반드시 시작 < 종료 조건 만족
        if (dto.getFromAt() != null && dto.getToAt() != null && !dto.getFromAt().isBefore(dto.getToAt())) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 정렬 규칙 검증
        Sort sort = parseSort(dto.getSort());


        return PageRequest.of(dto.getPage(), dto.getSize(), sort);
    }


    // 허용 정렬 필드
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startAt", "createdAt", "updatedAt");


    // 정렬 문자열 검증 및 변환
    private Sort parseSort(String sortValue) {

        if (sortValue == null || sortValue.isBlank()) {

            return Sort.by(Sort.Direction.ASC, "startAt", "id");
        }


        String[] parts = sortValue.trim().split(",", -1);


        if (parts.length != 2) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        String field = parts[0].trim();

        String directionValue = parts[1].trim().toLowerCase(Locale.ROOT);


        // 허용된 필드만 정렬 가능
        if (!ALLOWED_SORT_FIELDS.contains(field)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        Sort.Direction direction;

        switch (directionValue) {

            case "asc" -> direction = Sort.Direction.ASC;

            case "desc" -> direction = Sort.Direction.DESC;

            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 동일 시각 데이터의 정렬 순서를 일정하게 유지
        return Sort.by(direction, field, "id");
    }


    // 필수 ID 검증
    private void validateRequiredId(Long id) {

        if (id == null || id <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // 선택적 ID 검증
    private void validateOptionalId(Long id) {

        if (id != null && id <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // =====================================
    // 9. 예약 페이지의 DTO 일괄 변환
    // =====================================

    private Page<ScheduleReadDTO> mapPage(Page<MaintenanceSchedule> schedules, Long userId) {

        List<MaintenanceSchedule> content = schedules.getContent();


        // WorkOrder 일괄 조회
        Map<Long, WorkOrder> workOrders = loadWorkOrders(content);


        // 현재 Proposal 일괄 조회
        Map<Long, ScheduleProposal> proposals = loadProposals(content);


        // 한 페이지에서는 동일한 기준 시각 사용
        Instant now = clock.instant();


        return schedules.map(schedule -> {

            WorkOrder workOrder = workOrders.get(schedule.getWorkOrderId());


            // FK 및 데이터 정합성 확인
            if (workOrder == null) {

                throw new IllegalStateException("예약에 연결된 WorkOrder가 존재하지 않습니다.");
            }


            ScheduleProposal proposal = getCurrentProposal(schedule, proposals);


            return toReadDTO(schedule, workOrder, proposal, userId, now);
        });
    }


    // WorkOrder ID를 모아 일괄 조회
    private Map<Long, WorkOrder> loadWorkOrders(List<MaintenanceSchedule> schedules) {

        Set<Long> ids = schedules.stream().map(MaintenanceSchedule::getWorkOrderId).collect(Collectors.toSet());


        if (ids.isEmpty()) {

            return Map.of();
        }


        return workOrderRepository.findAllById(ids).stream().collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }


    // 현재 Proposal ID를 모아 일괄 조회
    private Map<Long, ScheduleProposal> loadProposals(List<MaintenanceSchedule> schedules) {

        Set<Long> ids = schedules.stream().map(MaintenanceSchedule::getCurrentProposalId).filter(Objects::nonNull).collect(Collectors.toSet());


        if (ids.isEmpty()) {

            return Map.of();
        }


        return proposalRepository.findAllById(ids).stream().collect(Collectors.toMap(ScheduleProposal::getId, Function.identity()));
    }


    // 일괄 조회된 Map에서 현재 Proposal 확인
    private ScheduleProposal getCurrentProposal(MaintenanceSchedule schedule, Map<Long, ScheduleProposal> proposals) {

        ScheduleProposal proposal = proposals.get(schedule.getCurrentProposalId());


        if (proposal == null || !Objects.equals(proposal.getScheduleId(), schedule.getId())) {

            throw new IllegalStateException("예약의 현재 Proposal 연결 정보가 올바르지 않습니다.");
        }


        return proposal;
    }


    // 예약 상세 조회
    private MaintenanceSchedule findSchedule(Long scheduleId) {

        validateRequiredId(scheduleId);


        return scheduleRepository.findById(scheduleId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }


    // WorkOrder 상세 조회
    private WorkOrder findWorkOrder(Long workOrderId) {

        validateRequiredId(workOrderId);


        return workOrderRepository.findById(workOrderId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }


    // 상세 조회 시 현재 Proposal 확인
    private ScheduleProposal findCurrentProposal(MaintenanceSchedule schedule) {

        if (schedule.getCurrentProposalId() == null) {

            throw new IllegalStateException("현재 Proposal이 연결되지 않은 예약입니다.");
        }


        return proposalRepository.findByIdAndScheduleId(schedule.getCurrentProposalId(), schedule.getId()).orElseThrow(() -> new IllegalStateException("현재 Proposal 연결 정보가 올바르지 않습니다."));
    }

    // =====================================
    // 10. 조회 전용 DTO 변환
    // =====================================

    private ScheduleReadDTO toReadDTO(

            MaintenanceSchedule schedule,

            WorkOrder workOrder,

            ScheduleProposal proposal,

            Long userId,

            Instant now

    ) {

        MaintenanceScheduleStatus status = schedule.getStatus();


        // 1. 현재 응답 대상 확인
        Long expectedResponderId = switch (status) {

            case PENDING -> schedule.getTechnicianId();

            case COUNTER_PROPOSED -> schedule.getRequestedBy();

            default -> null;
        };


        // 2. 응답 기한 경과 여부
        // Scheduler가 아직 상태를 EXPIRED로 변경하지
        // 않았더라도 기한 경과 시 응답 버튼 비활성화
        boolean responseExpired = status.isNegotiating() && (!now.isBefore(schedule.getNegotiationExpiresAt()) || proposal.isExpired(now));


        // 3. 현재 WorkOrder와 예약 기술자 일치 여부
        boolean currentTechnicianMatches = Objects.equals(workOrder.getAssignedTo(), schedule.getTechnicianId());


        // 4. 수락·거절·재제안 가능 여부
        boolean canRespond = status.isNegotiating() && proposal.getStatus().isPending() && !responseExpired && isBookableWorkOrder(workOrder) && currentTechnicianMatches && Objects.equals(expectedResponderId, userId) && !Objects.equals(proposal.getProposedBy(), userId);


        // 5. 예약 취소 가능 여부
        boolean canCancel = status.isActive() && !responseExpired && accessPolicy.canCancelSchedule(workOrder, schedule, userId);


        // 6. 방문 완료 가능 여부
        boolean canComplete = status == MaintenanceScheduleStatus.CONFIRMED && isBookableWorkOrder(workOrder) && !now.isBefore(schedule.getStartAt()) && accessPolicy.canCompleteSchedule(workOrder, schedule, userId);


        // 7. 조회 응답 생성
        return new ScheduleReadDTO(

                schedule.getId(),

                schedule.getWorkOrderId(),

                workOrder.getTitle(),

                workOrder.getOrganizationId(),

                workOrder.getComplexId(),

                workOrder.getLocationId(),

                workOrder.getAssetId(),

                workOrder.getStatus(),

                schedule.getTechnicianId(),

                schedule.getRequestedBy(),

                schedule.getStartAt(),

                schedule.getEndAt(),

                schedule.getStatus(),

                schedule.getCurrentProposalId(),

                ScheduleProposalResponseDTO.fromEntity(proposal),

                expectedResponderId,

                schedule.getNegotiationExpiresAt(),

                schedule.getConfirmedAt(),

                schedule.getCancelledBy(),

                schedule.getCancelledAt(),

                schedule.getCancellationReason(),

                schedule.getCompletedBy(),

                schedule.getCompletedAt(),

                schedule.getCreatedAt(),

                schedule.getUpdatedAt(),

                canRespond,

                canCancel,

                canComplete,

                responseExpired

        );
    }


    // 방문 예약 처리가 가능한 WorkOrder 상태
    private boolean isBookableWorkOrder(WorkOrder workOrder) {

        return workOrder.getStatus() == WorkOrderStatus.ASSIGNED || workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS;
    }
}