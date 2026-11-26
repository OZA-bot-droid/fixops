package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleExpirationService {

    private final BookingLockService bookingLockService;

    private final MaintenanceScheduleRepository scheduleRepository;

    private final Clock clock;


    // 개별 예약의 만료 처리
    // 메서드를 호출할 때마다 독립적인 트랜잭션을 시작
    // 호출하는 쪽에 이미 진행 중인 트랜잭션이 있더라도 일시 중단시키고
    // 새 트랜잭션은 부모 트랜잭션과 완전히 분리되어 독립적으로 커밋 또는 롤백
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireSchedule(Long scheduleId) {

        // 1. WorkOrder -> Schedule 순서로 잠금
        BookingLockService.LockedSchedule locked = bookingLockService.lockSchedule(scheduleId);

        MaintenanceSchedule schedule = locked.schedule();

        // 2. 현재 일정 협의 중인지 확인
        if (!schedule.getStatus().isNegotiating()) {

            return false;
        }

        // 3. 현재 Proposal 잠금
        ScheduleProposal proposal = bookingLockService.lockCurrentProposal(schedule, schedule.getCurrentProposalId());

        // 4. 현재 Proposal 상태 확인
        if (!proposal.getStatus().isPending()) {

            throw new IllegalStateException("협의 중인 예약의 현재 Proposal이 " + "PENDING 상태가 아닙니다.");
        }

        // 5. 잠금 이후 기준 시각 확인
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // 6. 전체 협의 기한 만료 여부
        boolean negotiationExpired = !now.isBefore(schedule.getNegotiationExpiresAt());

        // 7. 개별 Proposal 응답 기한 만료 여부
        boolean proposalExpired = proposal.isExpired(now);

        // 8. 아직 응답 가능한 예약이면 종료
        if (!negotiationExpired && !proposalExpired) {

            return false;
        }

        // 9. 기한 정합성 확인
        if (negotiationExpired && !proposalExpired) {

            throw new IllegalStateException("Proposal 응답 기한이 " + "전체 협의 기한을 초과합니다.");
        }

        // 10. 현재 Proposal 만료
        proposal.expire(now);

        // 11. MaintenanceSchedule 만료
        schedule.expire();

        // 12. 변경사항 DB 반영
        scheduleRepository.flush();

        return true;
    }
}