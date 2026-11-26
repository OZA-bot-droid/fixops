package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.workorder.domain.WorkOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BookingLifecycleService {

    private final MaintenanceScheduleRepository scheduleRepository;

    private final BookingLockService bookingLockService;

    private final Clock clock;


    // 기술자 재배정에 따른 활성 예약 자동 취소
    public void cancelForReassignment(WorkOrder workOrder, Long userId) {

        cancelActiveSchedule(workOrder, userId, "작업지시 담당 기술자 재배정으로 인한 자동 취소");
    }


    // 작업지시 취소에 따른 활성 예약 자동 취소
    public void cancelForWorkOrderCancellation(WorkOrder workOrder, Long userId) {

        cancelActiveSchedule(workOrder, userId, "작업지시 취소로 인한 자동 취소");
    }


    // 작업지시 완료 전 활성 예약 존재 여부 검증
    public void validateNoActiveScheduleForCompletion(WorkOrder workOrder) {

        // 활성 예약이 존재하는지 조회
        Optional<MaintenanceSchedule> activeSchedule = scheduleRepository.findActiveScheduleForUpdate(workOrder.getId(), MaintenanceScheduleStatus.activeStatuses());


        // 협의 중이거나 확정된 예약이 존재하면
        // 작업지시 완료 처리 불가
        if (activeSchedule.isPresent()) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }


    // 활성 예약 자동 취소 공통 처리
    private void cancelActiveSchedule(WorkOrder workOrder, Long userId, String reason) {

        // 1. 활성 예약 잠금 조회
        // 호출자가 WorkOrder 행을 먼저 잠근 상태여야 함
        Optional<MaintenanceSchedule> activeSchedule = scheduleRepository.findActiveScheduleForUpdate(workOrder.getId(), MaintenanceScheduleStatus.activeStatuses());


        // 2. 활성 예약이 없으면 추가 처리 없이 종료
        if (activeSchedule.isEmpty()) {

            return;
        }


        MaintenanceSchedule schedule = activeSchedule.get();


        // 3. 현재 Proposal 잠금 조회
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, schedule.getCurrentProposalId());


        // 4. 현재 처리 기준 시각
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);


        // 5. 일정 협의 중인 예약 처리
        if (schedule.getStatus().isNegotiating()) {

            // 협의 중인 예약은 현재 Proposal이
            // 반드시 PENDING 상태여야 함
            if (!proposal.getStatus().isPending()) {

                throw new IllegalStateException("협의 중인 예약의 현재 Proposal 상태가 " + "올바르지 않습니다.");
            }


            // 현재 Proposal 취소
            proposal.cancel(userId, reason, now);

        } else {

            // 6. 확정 예약은 ACCEPTED Proposal 유지
            if (schedule.getStatus() != MaintenanceScheduleStatus.CONFIRMED || proposal.getStatus() != ScheduleProposalStatus.ACCEPTED) {

                throw new IllegalStateException("활성 예약과 현재 Proposal의 상태가 " + "일치하지 않습니다.");
            }
        }


        // 7. MaintenanceSchedule 자동 취소
        schedule.cancel(userId, reason, now);


        // 8. 변경사항 DB 반영 및 제약조건 조기 검증
        scheduleRepository.flush();
    }
}