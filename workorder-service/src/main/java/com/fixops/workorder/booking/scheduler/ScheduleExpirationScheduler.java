package com.fixops.workorder.booking.scheduler;

import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import com.fixops.workorder.booking.repository.ScheduleProposalRepository;
import com.fixops.workorder.booking.service.ScheduleExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleExpirationScheduler {

    private final MaintenanceScheduleRepository scheduleRepository;

    private final ScheduleProposalRepository proposalRepository;

    private final ScheduleExpirationService expirationService;

    private final Clock clock;

    private static final int BATCH_SIZE = 100;


    // 설정된 주기(60초)마다 만료 대상 검사
    @Scheduled(fixedDelayString = "${booking.expiry-check-interval-ms:60000}")
    public void expireSchedules() {

        Instant now = clock.instant();

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);


        // 1. 전체 협의 기한이 만료된 예약 조회
        List<Long> negotiationExpiredIds = scheduleRepository.findNegotiationExpiredScheduleIds(MaintenanceScheduleStatus.negotiatingStatuses(), now, pageable);


        // 2. 개별 Proposal 기한이 만료된 예약 조회
        List<Long> proposalExpiredIds = proposalRepository.findExpiredProposalScheduleIds(ScheduleProposalStatus.PENDING, now, pageable);


        // 3. 만료 대상 중복 제거
        Set<Long> expiredScheduleIds = new LinkedHashSet<>();

        expiredScheduleIds.addAll(negotiationExpiredIds);

        expiredScheduleIds.addAll(proposalExpiredIds);


        if (expiredScheduleIds.isEmpty()) {

            return;
        }


        int successCount = 0;
        int skippedCount = 0;
        int failureCount = 0;


        // 4. 개별 예약 만료 처리
        for (Long scheduleId : expiredScheduleIds) {

            try {

                boolean expired = expirationService.expireSchedule(scheduleId);

                if (expired) {

                    successCount++;

                    log.info("예약 협의 만료 완료. scheduleId={}", scheduleId);

                } else {

                    skippedCount++;
                }

            } catch (Exception e) {

                failureCount++;

                log.error("예약 만료 처리 실패. scheduleId={}", scheduleId, e);
            }
        }

        // 5. 처리 결과 로그
        log.info("예약 만료 검사 완료. 대상={}, 성공={}, " + "건너뜀={}, 실패={}", expiredScheduleIds.size(), successCount, skippedCount, failureCount);
    }
}