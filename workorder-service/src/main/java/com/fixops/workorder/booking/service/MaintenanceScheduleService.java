package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.BookingIdempotency;
import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import com.fixops.workorder.booking.dto.ScheduleResponseDTO;
import com.fixops.workorder.booking.dto.ScheduleSaveDTO;
import com.fixops.workorder.booking.policy.BookingAccessPolicy;
import com.fixops.workorder.booking.policy.BookingDeadlinePolicy;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import com.fixops.workorder.booking.repository.ScheduleProposalRepository;
import com.fixops.workorder.booking.util.BookingRequestHashGenerator;
import com.fixops.workorder.common.response.ApiResponse;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.workorder.domain.WorkOrder;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import com.fixops.workorder.workorder.repository.WorkOrderRepository;
import com.fixops.workorder.booking.dto.ScheduleCancelDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceScheduleService {

    private final MaintenanceScheduleRepository scheduleRepository;

    private final ScheduleProposalRepository proposalRepository;

    private final WorkOrderRepository workOrderRepository;

    private final BookingIdempotencyService idempotencyService;

    private final BookingLockService bookingLockService;

    private final BookingRequestHashGenerator hashGenerator;

    private final BookingDeadlinePolicy deadlinePolicy;

    private final BookingAccessPolicy accessPolicy;

    private final JsonMapper jsonMapper;

    private final Clock clock;

    // 최초 예약 요청
    public BookingIdempotencyService.StoredResponse createSchedule(ScheduleSaveDTO dto, Long userId, String requestKey) {

        // 1. 기본 입력값 검증
        accessPolicy.validateActor(userId);

        validateCreateInput(dto);

        idempotencyService.validateRequestKey(requestKey);


        // 2. WorkOrder 잠금 조회
        WorkOrder workOrder = workOrderRepository.findByIdForUpdate(dto.getWorkOrderId()).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));


        // 3. 예약 요청 권한 확인
        accessPolicy.validateCreateSchedule(workOrder, userId);


        // 4. 요청 해시 계산
        String requestHash = hashGenerator.generateScheduleHash(dto);


        // 5. 멱등성 요청 키 등록
        BookingIdempotencyService.ClaimResult claim = idempotencyService.claim(workOrder.getId(), workOrder.getOrganizationId(), userId, BookingIdempotencyService.CREATE_SCHEDULE, requestKey, requestHash, clock.instant());


        BookingIdempotency idempotency = claim.idempotency();


        // 6. 기존 요청이면 최초 응답 반환
        if (!claim.newlyCreated()) {

            return new BookingIdempotencyService.StoredResponse(idempotency.getResponseStatus(), idempotency.getResponseBody());
        }


        // 7. 대기 시간이 발생할 수 있기 때문에 잠금 및 요청 키 처리 이후 기준 시각 결정
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);


        // 8. WorkOrder 예약 가능 상태 검증
        validateBookableWorkOrder(workOrder);


        // 9. 담당 기술자 확인
        Long technicianId = workOrder.getAssignedTo();

        if (Objects.equals(technicianId, userId)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 10. 방문 희망 시간 검증
        deadlinePolicy.validateProposedTime(dto.getStartAt(), dto.getEndAt(), now);


        // 11. 동일 WorkOrder 활성 예약 확인
        validateNoActiveSchedule(workOrder.getId());


        // 12. 기술자 점유 시간 충돌 검사
        validateNoTimeConflict(technicianId, dto.getStartAt(), dto.getEndAt());


        // 13. 전체 협의 유효기간 계산
        Instant negotiationExpiresAt = deadlinePolicy.calculateNegotiationExpiresAt(now);


        // 14. 최초 Proposal 응답 기한 계산
        Instant proposalExpiresAt = deadlinePolicy.calculateProposalExpiresAt(MaintenanceScheduleStatus.PENDING, now, dto.getStartAt(), negotiationExpiresAt);


        // 15. MaintenanceSchedule 생성
        MaintenanceSchedule schedule = MaintenanceSchedule.builder().workOrderId(workOrder.getId()).technicianId(technicianId).requestedBy(userId).startAt(dto.getStartAt()).endAt(dto.getEndAt()).status(MaintenanceScheduleStatus.PENDING).negotiationExpiresAt(negotiationExpiresAt).build();

        // saveAndFlush - 최초 Schedule을 저장한 후 Schedule ID 발급뿐 아니라 DB 제약 위반을 해당 작업 중 명확하게 확인하기 위함
        // 만약 saveAndFlush()한 이후 Proposal 생성에 실패하면 Schedule INSERT도 롤백
        scheduleRepository.saveAndFlush(schedule);


        // 16. 최초 ScheduleProposal 생성
        ScheduleProposal proposal = ScheduleProposal.builder().scheduleId(schedule.getId()).proposedBy(userId).startAt(dto.getStartAt()).endAt(dto.getEndAt()).status(ScheduleProposalStatus.PENDING).reason(hashGenerator.normalizeReason(dto.getReason())).expiresAt(proposalExpiresAt).createdAt(now).build();

        proposalRepository.saveAndFlush(proposal);


        // 17. current_proposal_id에 최초 최초 Proposal 연결
        schedule.initializeProposal(proposal.getId());

        scheduleRepository.flush();


        // 18. 응답 DTO 생성
        ScheduleResponseDTO responseDTO = ScheduleResponseDTO.fromEntity(schedule, proposal);


        // 19. 공통 API 응답 생성
        ApiResponse<ScheduleResponseDTO> response = ApiResponse.success(responseDTO);


        // 20. JSON 응답 생성
        String responseBody = jsonMapper.writeValueAsString(response);


        int responseStatus = HttpStatus.CREATED.value();


        // 21. 최초 응답 저장
        idempotencyService.saveResponse(idempotency, responseStatus, responseBody);


        // 22. 응답 반환
        return new BookingIdempotencyService.StoredResponse(responseStatus, responseBody);
    }


    // 예약 취소
    public BookingIdempotencyService.StoredResponse cancelSchedule(Long scheduleId, ScheduleCancelDTO dto, Long userId, String requestKey) {

        // 1. 기본 입력값 검증
        validateScheduleActionInput(scheduleId, userId, requestKey);


        if (dto == null || dto.getReason() == null || dto.getReason().isBlank() || dto.getReason().length() > 1000) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        String reason = hashGenerator.normalizeReason(dto.getReason());


        // 2. WorkOrder 및 MaintenanceSchedule 잠금
        BookingLockService.LockedSchedule locked = bookingLockService.lockSchedule(scheduleId);


        WorkOrder workOrder = locked.workOrder();

        MaintenanceSchedule schedule = locked.schedule();


        // 3. 예약 취소 권한 검증
        accessPolicy.validateCancelSchedule(workOrder, schedule, userId);


        // 4. 요청 Hash 생성
        String requestHash = hashGenerator.generateCancelHash(scheduleId, reason);


        // 5. 멱등성 요청 확인
        BookingIdempotencyService.ClaimResult claim = idempotencyService.claim(workOrder.getId(), workOrder.getOrganizationId(), userId,
                                                                BookingIdempotencyService.CANCEL_SCHEDULE, requestKey, requestHash, clock.instant());


        // 6. 기존 성공 요청 응답 반환
        if (!claim.newlyCreated()) {

            return replayScheduleResponse(claim.idempotency());
        }


        // 7. 취소 가능한 예약 상태 검증
        if (!schedule.getStatus().isActive()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 8. 현재 Proposal 잠금
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, schedule.getCurrentProposalId());


        // 9. 기준 시각 결정
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);


        // 10. 일정 협의 중인 예약 취소
        if (schedule.getStatus().isNegotiating()) {

            if (!proposal.getStatus().isPending()) {

                throw new IllegalStateException("협의 중인 예약의 현재 Proposal 상태가 " + "올바르지 않습니다.");
            }


            // 이미 만료된 협의는 자동 만료 처리 대상으로 유지
            schedule.validateNegotiationDeadline(now);

            if (proposal.isExpired(now)) {

                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
            }


            // 현재 Proposal도 취소
            proposal.cancel(userId, reason, now);

        } else {

            // CONFIRMED 예약의 현재 Proposal은 ACCEPTED 유지
            if (proposal.getStatus() != ScheduleProposalStatus.ACCEPTED) {

                throw new IllegalStateException("확정 예약의 현재 Proposal 상태가 " + "ACCEPTED가 아닙니다.");
            }
        }


        // 11. MaintenanceSchedule 취소
        schedule.cancel(userId, reason, now);


        // 12. 변경사항 및 응답 저장
        return saveScheduleActionResponse(claim.idempotency(), schedule, proposal);
    }

    // 방문 완료
    public BookingIdempotencyService.StoredResponse completeSchedule(Long scheduleId, Long userId, String requestKey) {

        // 1. 기본 입력값 검증
        validateScheduleActionInput(scheduleId, userId, requestKey);


        // 2. WorkOrder 및 MaintenanceSchedule 잠금
        BookingLockService.LockedSchedule locked = bookingLockService.lockSchedule(scheduleId);


        WorkOrder workOrder = locked.workOrder();

        MaintenanceSchedule schedule = locked.schedule();


        // 3. 방문 완료 권한 검증
        accessPolicy.validateCompleteSchedule(workOrder, schedule, userId);


        // 4. 요청 Hash 생성
        String requestHash = hashGenerator.generateCompleteHash(scheduleId);


        // 5. 멱등성 요청 확인
        BookingIdempotencyService.ClaimResult claim = idempotencyService.claim(workOrder.getId(), workOrder.getOrganizationId(), userId,
                                                                BookingIdempotencyService.COMPLETE_SCHEDULE, requestKey, requestHash, clock.instant());


        // 6. 기존 성공 요청 응답 반환
        if (!claim.newlyCreated()) {

            return replayScheduleResponse(claim.idempotency());
        }


        // 7. 확정 예약 여부 확인
        if (schedule.getStatus() != MaintenanceScheduleStatus.CONFIRMED) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 8. WorkOrder 상태 및 기술자 확인
        validateBookableWorkOrder(workOrder);


        // 9. 현재 Proposal 잠금
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, schedule.getCurrentProposalId());


        if (proposal.getStatus() != ScheduleProposalStatus.ACCEPTED) {

            throw new IllegalStateException("확정 예약의 Proposal 상태가 " + "ACCEPTED가 아닙니다.");
        }


        // 10. 기준 시각 결정
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);


        // 11. 방문 완료
        schedule.complete(userId,now);


        // 12. 변경사항 및 응답 저장
        return saveScheduleActionResponse(claim.idempotency(), schedule, proposal);
    }

    // 최초 예약 요청 입력값 검증
    private void validateCreateInput(ScheduleSaveDTO dto) {

        if (dto == null || dto.getWorkOrderId() == null || dto.getWorkOrderId() <= 0 || dto.getStartAt() == null || dto.getEndAt() == null || !dto.getStartAt().isBefore(dto.getEndAt())) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        if (dto.getReason() != null && dto.getReason().length() > 1000) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // PostgreSQL TIMESTAMPTZ 정밀도와 일치하도록 제한
        if (dto.getStartAt().getNano() % 1000 != 0 || dto.getEndAt().getNano() % 1000 != 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // WorkOrder 예약 가능 상태 검증
    private void validateBookableWorkOrder(WorkOrder workOrder) {

        WorkOrderStatus status = workOrder.getStatus();


        if (status != WorkOrderStatus.ASSIGNED && status != WorkOrderStatus.IN_PROGRESS) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        if (workOrder.getAssignedTo() == null || workOrder.getAssignedTo() <= 0) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    // 동일 WorkOrder 활성 예약 검증
    private void validateNoActiveSchedule(Long workOrderId) {

        boolean exists = scheduleRepository.existsByWorkOrderIdAndStatusIn(workOrderId, MaintenanceScheduleStatus.activeStatuses());


        if (exists) {

            throw new BusinessException(ErrorCode.BOOKING_CONFLICT);
        }
    }

    // 기술자 점유 시간 충돌 검증
    private void validateNoTimeConflict(Long technicianId, Instant startAt, Instant endAt) {

        boolean overlaps = scheduleRepository.existsOverlappingSchedule(technicianId, MaintenanceScheduleStatus.occupyingStatuses(), startAt, endAt);


        if (overlaps) {

            throw new BusinessException(ErrorCode.BOOKING_CONFLICT);
        }
    }

    // 예약 상태 변경 요청 기본 검증
    private void validateScheduleActionInput(Long scheduleId, Long userId, String requestKey) {

        accessPolicy.validateActor(userId);


        idempotencyService.validateRequestKey(requestKey);


        if (scheduleId == null || scheduleId <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // 최초 상태 변경 응답 저장
    private BookingIdempotencyService.StoredResponse saveScheduleActionResponse(BookingIdempotency idempotency, MaintenanceSchedule schedule,
                                                                                ScheduleProposal proposal) {

        // 상태 변경 및 DB 제약조건 확인
        scheduleRepository.flush();


        ScheduleResponseDTO responseDTO = ScheduleResponseDTO.fromEntity(schedule, proposal);


        ApiResponse<ScheduleResponseDTO> response = ApiResponse.success(responseDTO);


        String responseBody = jsonMapper.writeValueAsString(response);


        int responseStatus = HttpStatus.OK.value();


        idempotencyService.saveResponse(idempotency, responseStatus, responseBody);


        return new BookingIdempotencyService.StoredResponse(responseStatus, responseBody);
    }


    // 기존 성공 응답 반환
    private BookingIdempotencyService.StoredResponse replayScheduleResponse(BookingIdempotency idempotency) {

        return new BookingIdempotencyService.StoredResponse(idempotency.getResponseStatus(), idempotency.getResponseBody());
    }
}