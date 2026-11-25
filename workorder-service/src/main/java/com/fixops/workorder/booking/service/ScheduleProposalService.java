package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.BookingIdempotency;
import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import com.fixops.workorder.booking.dto.ScheduleProposalSaveDTO;
import com.fixops.workorder.booking.dto.ScheduleProposalRejectDTO;
import com.fixops.workorder.booking.dto.ScheduleResponseDTO;
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
public class ScheduleProposalService {

    private final MaintenanceScheduleRepository scheduleRepository;

    private final ScheduleProposalRepository proposalRepository;

    private final BookingLockService bookingLockService;

    private final BookingIdempotencyService idempotencyService;

    private final BookingRequestHashGenerator hashGenerator;

    private final BookingAccessPolicy accessPolicy;

    private final BookingDeadlinePolicy deadlinePolicy;

    private final JsonMapper jsonMapper;

    private final Clock clock;

    // 현재 시간 제안 수락
    public BookingIdempotencyService.StoredResponse acceptProposal(Long scheduleId, Long proposalId, Long userId, String requestKey) {

        // 1. 기본 요청값 검증
        validateRequest(scheduleId, proposalId, userId, requestKey);


        // 2. 요청 Hash 생성
        String requestHash = hashGenerator.generateAcceptHash(scheduleId, proposalId);


        // 3. WorkOrder 및 Schedule 잠금
        ProposalRequestContext context = prepareRequest(scheduleId, userId, BookingIdempotencyService.ACCEPT_PROPOSAL, requestKey, requestHash);


        // 4. 기존 성공 요청이면 최초 응답 반환
        if (!context.claim().newlyCreated()) {

            return replayResponse(context.claim().idempotency());
        }


        MaintenanceSchedule schedule = context.schedule();


        // 5. 현재 Proposal 잠금
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, proposalId);


        // 6. 기준 시각 조회
        Instant now = currentTime();


        // 7. 현재 상태, 권한, 응답 기한 검증
        validateNegotiation(context.workOrder(), schedule, proposal, userId, now);


        // 8. 다른 예약과 시간 충돌 확인
        validateNoTimeConflict(schedule, schedule.getStartAt(), schedule.getEndAt());


        // 9. Proposal 수락
        proposal.accept(userId, now);


        // 10. 예약 확정
        schedule.confirm(proposal.getId(), now);


        // 11. DB 변경 및 응답 저장
        return saveResponse(context.claim().idempotency(), schedule, proposal, HttpStatus.OK);
    }


    // 현재 시간 제안 거절
    public BookingIdempotencyService.StoredResponse rejectProposal(Long scheduleId, Long proposalId, ScheduleProposalRejectDTO dto, Long userId, String requestKey) {

        // 1. 기본 입력값 검증
        validateRequest(scheduleId, proposalId, userId, requestKey);


        if (dto == null || dto.getReason() == null || dto.getReason().isBlank() || dto.getReason().length() > 1000) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        String reason = hashGenerator.normalizeReason(dto.getReason());


        // 2. 요청 Hash 생성
        String requestHash = hashGenerator.generateRejectHash(scheduleId, proposalId, reason);


        // 3. 공통 잠금 및 멱등성 검증
        ProposalRequestContext context = prepareRequest(scheduleId, userId, BookingIdempotencyService.REJECT_PROPOSAL, requestKey, requestHash);


        // 4. 기존 성공 요청 반환
        if (!context.claim().newlyCreated()) {

            return replayResponse(context.claim().idempotency());
        }


        MaintenanceSchedule schedule = context.schedule();


        // 5. 현재 Proposal 잠금
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, proposalId);


        Instant now = currentTime();


        // 6. 상태 및 응답 권한 검증
        validateNegotiation(context.workOrder(), schedule, proposal, userId, now);


        // 7. Proposal 거절
        proposal.reject(userId, reason, now);


        // 8. 전체 예약 협의 종료
        schedule.reject();


        // 9. DB 변경 및 응답 저장
        return saveResponse(context.claim().idempotency(), schedule, proposal, HttpStatus.OK);
    }


    // 대체 시간 제안 및 재제안
    public BookingIdempotencyService.StoredResponse createProposal(Long scheduleId, ScheduleProposalSaveDTO dto, Long userId, String requestKey) {

        // 1. 입력값 검증
        validateProposalInput(dto);

        validateRequest(scheduleId, dto.getCurrentProposalId(), userId, requestKey);


        // 2. 요청 Hash 생성
        String requestHash = hashGenerator.generateProposalHash(scheduleId, dto);


        // 3. 공통 잠금 및 멱등성 검증
        ProposalRequestContext context = prepareRequest(scheduleId, userId, BookingIdempotencyService.CREATE_PROPOSAL, requestKey, requestHash);


        // 4. 기존 성공 요청 반환
        if (!context.claim().newlyCreated()) {

            return replayResponse(context.claim().idempotency());
        }


        MaintenanceSchedule schedule = context.schedule();


        // 5. 기존 활성 Proposal 잠금
        ScheduleProposal currentProposal = bookingLockService.lockCurrentProposal(schedule, dto.getCurrentProposalId());


        Instant now = currentTime();


        // 6. 상태, 권한 및 기존 응답 기한 검증
        validateNegotiation(context.workOrder(), schedule, currentProposal, userId, now);


        // 7. 신규 제안 시간 검증
        deadlinePolicy.validateProposedTime(dto.getStartAt(), dto.getEndAt(), now);


        // 8. 현재와 동일한 시간 재제안 방지
        if (Objects.equals(schedule.getStartAt(), dto.getStartAt()) && Objects.equals(schedule.getEndAt(), dto.getEndAt())) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 9. 신규 제안 이후의 예약 상태 결정
        MaintenanceScheduleStatus targetStatus = switch (schedule.getStatus()) {

            // 기술자의 대체 시간 제안
            case PENDING -> MaintenanceScheduleStatus.COUNTER_PROPOSED;

            // 요청자의 시간 재제안
            case COUNTER_PROPOSED -> MaintenanceScheduleStatus.PENDING;

            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        };


        // 10. 다른 예약과 신규 제안 시간 충돌 검증
        validateNoTimeConflict(schedule, dto.getStartAt(), dto.getEndAt());


        // 11. 신규 Proposal 응답 기한 계산
        Instant proposalExpiresAt = deadlinePolicy.calculateProposalExpiresAt(targetStatus, now, dto.getStartAt(), schedule.getNegotiationExpiresAt());


        String reason = hashGenerator.normalizeReason(dto.getReason());


        // 12. 기존 Proposal 종료
        currentProposal.supersede(userId, reason, now);


        // 기존 PENDING 상태를 먼저 DB에 반영
        // 하나의 Schedule에는 PENDING Proposal이 최대 1개만 존재할 수 있음
        // 기존 Proposal을 SUPERSEDED로 변경했더라도 Hibernate가 아직 UPDATE SQL을 실행하지 않았다면 DB에서는 여전히 PENDING 상태임.
        // 이 상태에서 신규 Proposal을 INSERT하면 UNIQUE 제약 위반이 발생 가능성 있음
        // 기존 Proposal의 변경사항을 먼저 flush하여 DB에 반영한 뒤 신규 Proposal을 생성함.
        // 이후 신규 Proposal 생성이나 예약 시간 변경에 실패하면 기존 SUPERSEDED 변경까지 전부 롤백
        proposalRepository.flush();


        // 13. 신규 Proposal 생성
        ScheduleProposal newProposal = ScheduleProposal.builder()
                                                        .scheduleId(schedule.getId())
                                                        .proposedBy(userId)
                                                        .startAt(dto.getStartAt())
                                                        .endAt(dto.getEndAt())
                                                        .status(ScheduleProposalStatus.PENDING)
                                                        .reason(reason)
                                                        .expiresAt(proposalExpiresAt)
                                                        .createdAt(now)
                                                        .build();


        proposalRepository.saveAndFlush(newProposal);


        // 14. MaintenanceSchedule 상태 및 방문 시간 변경
        if (targetStatus == MaintenanceScheduleStatus.COUNTER_PROPOSED) {

            schedule.counterPropose(newProposal.getId(), newProposal.getStartAt(), newProposal.getEndAt());

        } else {

            schedule.rePropose(newProposal.getId(), newProposal.getStartAt(), newProposal.getEndAt());
        }


        // 15. DB 변경 및 응답 저장
        return saveResponse(context.claim().idempotency(), schedule, newProposal, HttpStatus.CREATED);
    }


    // 공통 요청 준비
    private ProposalRequestContext prepareRequest(Long scheduleId, Long userId, String operation, String requestKey, String requestHash) {

        // WorkOrder 및 MaintenanceSchedule 순서대로 잠금
        BookingLockService.LockedSchedule locked = bookingLockService.lockSchedule(scheduleId);


        WorkOrder workOrder = locked.workOrder();

        MaintenanceSchedule schedule = locked.schedule();


        // 해당 예약에 참여할 수 있는 사용자 확인
        accessPolicy.validateNegotiationParticipant(schedule, userId);


        // 멱등성 요청 키 등록
        BookingIdempotencyService.ClaimResult claim = idempotencyService.claim(workOrder.getId(), workOrder.getOrganizationId(), userId,
                                                                                operation, requestKey, requestHash, currentTime());


        return new ProposalRequestContext(workOrder, schedule, claim);
    }


    // 예약 협의 상태 및 권한 검증
    private void validateNegotiation(WorkOrder workOrder, MaintenanceSchedule schedule, ScheduleProposal proposal, Long userId, Instant now) {

        // 1. 현재 일정 협의 상태 확인
        if (!schedule.getStatus().isNegotiating()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 2. 현재 Proposal 응답 대기 여부
        if (!proposal.getStatus().isPending()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 3. 현재 응답 주체 검증
        accessPolicy.validateProposalResponder(schedule, proposal, userId);


        // 4. WorkOrder 예약 가능 상태 확인
        WorkOrderStatus workOrderStatus = workOrder.getStatus();

        if (workOrderStatus != WorkOrderStatus.ASSIGNED && workOrderStatus != WorkOrderStatus.IN_PROGRESS) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 5. 담당 기술자가 변경되었는지 확인
        if (!Objects.equals(workOrder.getAssignedTo(), schedule.getTechnicianId())) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 6. 전체 협의 기한 확인
        schedule.validateNegotiationDeadline(now);


        // 7. 개별 응답 기한 확인
        if (proposal.isExpired(now)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }


        // 8. 현재 Proposal의 제안자 정합성 확인
        Long expectedProposerId = switch (schedule.getStatus()) {

            case PENDING -> schedule.getRequestedBy();

            case COUNTER_PROPOSED -> schedule.getTechnicianId();

            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        };


        if (!Objects.equals(proposal.getProposedBy(), expectedProposerId)) {

            throw new IllegalStateException("예약과 현재 Proposal의 제안자가 일치하지 않습니다.");
        }


        // 9. 예약 시간과 Proposal 시간 정합성 확인
        if (!Objects.equals(schedule.getStartAt(), proposal.getStartAt()) || !Objects.equals(schedule.getEndAt(), proposal.getEndAt())) {

            throw new IllegalStateException("예약과 현재 Proposal의 시간이 일치하지 않습니다.");
        }
    }


    // 시간 중복 검증
    private void validateNoTimeConflict(MaintenanceSchedule schedule, Instant startAt, Instant endAt) {

        boolean overlaps = scheduleRepository.existsOverlappingScheduleExcludingSelf(schedule.getTechnicianId(), MaintenanceScheduleStatus.occupyingStatuses(),
                                                                                    startAt, endAt, schedule.getId());


        if (overlaps) {

            throw new BusinessException(ErrorCode.BOOKING_CONFLICT);
        }
    }


    // 요청 기본 검증
    private void validateRequest(Long scheduleId, Long proposalId, Long userId, String requestKey) {

        accessPolicy.validateActor(userId);

        idempotencyService.validateRequestKey(requestKey);


        if (scheduleId == null || scheduleId <= 0 || proposalId == null || proposalId <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // 신규 Proposal 입력값 검증
    private void validateProposalInput(ScheduleProposalSaveDTO dto) {

        if (dto == null || dto.getCurrentProposalId() == null || dto.getCurrentProposalId() <= 0 ||
                dto.getStartAt() == null || dto.getEndAt() == null || !dto.getStartAt().isBefore(dto.getEndAt())) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        if (dto.getReason() != null && dto.getReason().length() > 1000) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // TIMESTAMPTZ 정밀도 검증
        if (dto.getStartAt().getNano() % 1000 != 0 || dto.getEndAt().getNano() % 1000 != 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // 응답 저장
    private BookingIdempotencyService.StoredResponse saveResponse(BookingIdempotency idempotency, MaintenanceSchedule schedule,
                                                                  ScheduleProposal proposal, HttpStatus httpStatus) {

        // 변경사항 및 DB 제약 위반 확인
        scheduleRepository.flush();


        ScheduleResponseDTO responseDTO = ScheduleResponseDTO.fromEntity(schedule, proposal);


        ApiResponse<ScheduleResponseDTO> response = ApiResponse.success(responseDTO);


        String responseBody = jsonMapper.writeValueAsString(response);


        int responseStatus = httpStatus.value();


        idempotencyService.saveResponse(idempotency, responseStatus, responseBody);


        return new BookingIdempotencyService.StoredResponse(responseStatus, responseBody);
    }


    // 기존 멱등성 응답 반환
    private BookingIdempotencyService.StoredResponse replayResponse(BookingIdempotency idempotency) {

        return new BookingIdempotencyService.StoredResponse(idempotency.getResponseStatus(), idempotency.getResponseBody());
    }


    // 공통 기준 시각
    private Instant currentTime() {

        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }


    // 공통 요청 처리 결과
    private record ProposalRequestContext(WorkOrder workOrder, MaintenanceSchedule schedule,
                                          BookingIdempotencyService.ClaimResult claim) {
    }
}