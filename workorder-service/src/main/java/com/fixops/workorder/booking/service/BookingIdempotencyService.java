package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.BookingIdempotency;
import com.fixops.workorder.booking.repository.BookingIdempotencyRepository;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY) // 이미 실행 중인 트랜잭션이 반드시 존재해야 한다는 의미. 서비스가 독립적으로 새로운 트랜잭션을 생성하지 않도록 하기 위한 설정
public class BookingIdempotencyService {

    private final BookingIdempotencyRepository repository;


    // 작업 구분값
    // 최초 예약 요청
    public static final String CREATE_SCHEDULE = "CREATE_SCHEDULE";

    // 기술자 대체 제안 or 요청자 재제안
    public static final String CREATE_PROPOSAL = "CREATE_PROPOSAL";

    // 현재 제안 수락
    public static final String ACCEPT_PROPOSAL = "ACCEPT_PROPOSAL";

    // 현재 제안 거절
    public static final String REJECT_PROPOSAL = "REJECT_PROPOSAL";

    // 요청 키 등록 및 기존 요청 확인
    public ClaimResult claim(Long workOrderId, Long organizationId, Long actorId, String operation, String requestKey, String requestHash, Instant now) {

        validateRequestKey(requestKey);


        // 신규 키 등록 시도
        int inserted = repository.insertIfAbsent(workOrderId, organizationId, actorId, operation, requestKey, requestHash, now);


        // 신규 또는 기존 멱등성 데이터 조회
        BookingIdempotency idempotency = repository.findByOrganizationIdAndActorIdAndOperationAndRequestKey(organizationId, actorId, operation, requestKey).orElseThrow(() -> new IllegalStateException("멱등성 요청 조회 실패"));


        // 동일 Key에 다른 요청을 사용한 경우
        if (!Objects.equals(idempotency.getRequestHash(), requestHash) || !Objects.equals(idempotency.getWorkOrderId(), workOrderId)) {

            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSE);
        }


        // 최초 요청
        if (inserted == 1) {

            return new ClaimResult(idempotency, true);
        }


        // 기존 요청인데 저장된 응답이 없는 경우
        if (!idempotency.hasResponse()) {

            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }


        // 동일 요청 재시도
        return new ClaimResult(idempotency, false);
    }


    // 최초 처리 결과 저장
    public void saveResponse(BookingIdempotency idempotency, int responseStatus, String responseBody) {

        idempotency.saveResponse(responseStatus, responseBody);

        repository.flush();
    }


    // Idempotency-Key 검증
    public void validateRequestKey(String requestKey) {

        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 200) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }


    // 요청 키 등록 결과
    public record ClaimResult(BookingIdempotency idempotency, boolean newlyCreated) {
    }


    // HTTP 응답 보존용 객체
    public record StoredResponse(int status, String body) {
    }
}