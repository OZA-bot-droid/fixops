package com.fixops.workorder.common.response;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 유효하지 않은 입력 - 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "유효하지 않은 입력입니다."),

    // 	로그인·JWT 부재/만료 - 401 Unauthorized
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다."),

    // 	조직 또는 단지·세대·작업 권한 없음 - 403 Forbidden
    ORG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORG_ACCESS_DENIED", "접근할 권한이 없습니다."),

    // 리소스 미존재 또는 비공개 정책 - 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),

    // 작업자 겹침·작업당 활성 예약 중복 - 409 Conflict
    BOOKING_CONFLICT(HttpStatus.CONFLICT, "BOOKING_CONFLICT", "예약 시간이 중복되거나 이미 예약된 작업입니다."),

    // 상태 전이 불허 - 409 Conflict
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "허용되지 않는 상태 변경입니다."),

//    // 차감 가능한 재고 부족(업무 충돌) - 409 Conflict
//    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."),

    // 동일 키로 다른 요청 본문 재사용 - 409 Conflict
    IDEMPOTENCY_KEY_REUSE(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE", "동일한 요청 키를 다른 요청에 사용할 수 없습니다."),

    // OTP·가입·조회 과다 - 429 Too Many Requests
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청 횟수가 제한을 초과했습니다."),

    // 외부 K-apt/내부 DB·gRPC 의존성 장애 - 503 Service Unavailable
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "외부 또는 내부 서비스에 연결할 수 없습니다."),

    // LLM 응답 불가 - 503
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", "AI 서비스에 연결할 수 없습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}