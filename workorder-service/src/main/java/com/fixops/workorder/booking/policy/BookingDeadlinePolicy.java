package com.fixops.workorder.booking.policy;

import com.fixops.workorder.booking.config.BookingProperties;
import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class BookingDeadlinePolicy { // Service가 전달한 기준 시각과 설정값으로 만료 기한 계산

    private final BookingProperties bookingProperties;


    // 전체 일정 협의 유효기간 계산(재제안시 사용X)
    public Instant calculateNegotiationExpiresAt(Instant now) {

        if (now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 최초 예약 요청 시 전체 일정 협의 만료 시각을 계산
        return now.plus(bookingProperties.negotiationExpiryDuration());
    }


    // 개별 Proposal 응답 기한 계산
    public Instant calculateProposalExpiresAt(MaintenanceScheduleStatus targetStatus, Instant now, Instant startAt, Instant negotiationExpiresAt) {

        if (targetStatus == null || now == null || startAt == null || negotiationExpiresAt == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 전체 협의 유효기간 확인
        validateNegotiationDeadline(now, negotiationExpiresAt);


        // 상태별 응답 가능 시간 계산
        // 현재 상태가 아니라 새로운 제안을 생성한 후 변경될 예약 상태를 전달
        Duration responseDuration = switch (targetStatus) {

            // 기술자 응답 대기
            case PENDING -> bookingProperties.technicianResponseDuration();

            // 입주민 또는 관리자 응답 대기
            case COUNTER_PROPOSED -> bookingProperties.requesterResponseDuration();

            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        };


        // 개별 응답 기한
        Instant responseDeadline = now.plus(responseDuration);


        // 방문 전 최소 준비시간 기준 마감 시각
        Instant visitDeadline = startAt.minus(bookingProperties.minimumLeadDuration());


        // 세 가지 기한 중 가장 빠른 시각 선택
        Instant expiresAt = Stream.of(responseDeadline, visitDeadline, negotiationExpiresAt).min(Comparator.naturalOrder()).orElseThrow();


        // 응답 가능 시간이 남아 있는지 확인
        if (!expiresAt.isAfter(now)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return expiresAt;
    }


    // =================================================
    // 3. 예약 및 제안 시간 검증
    // =================================================

    public void validateProposedTime(Instant startAt, Instant endAt, Instant now) {

        if (startAt == null || endAt == null || now == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 시작 시각은 종료 시각보다 이전이어야 함
        if (!startAt.isBefore(endAt)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // 최소 준비시간을 확보할 수 있는지 확인
        Instant visitDeadline = startAt.minus(bookingProperties.minimumLeadDuration());

        if (!now.isBefore(visitDeadline)) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // =================================================
    // 4. 전체 협의 유효기간 검증
    // =================================================

    public void validateNegotiationDeadline(Instant now, Instant negotiationExpiresAt) {

        if (now == null || negotiationExpiresAt == null) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!now.isBefore(negotiationExpiresAt)) {

            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}