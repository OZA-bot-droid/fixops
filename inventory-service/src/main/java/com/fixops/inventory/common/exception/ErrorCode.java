package com.fixops.inventory.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 목록 (docs/04 4장 "오류 코드 매핑"이 기준).
 * 코드 이름, HTTP 상태, 화면에 보여줄 기본 메시지를 한 곳에 모아 둔다.
 * 코드를 새로 만들거나 바꿀 때는 docs/04 와 OpenAPI(ErrorEnvelope.code enum)도 함께 맞춘다.
 */
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ORG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근할 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    IDEMPOTENCY_KEY_REUSE(HttpStatus.CONFLICT, "같은 요청 키로 다른 내용을 보낼 수 없습니다."),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해 주세요."),

    // docs/04 에 없는 코드. 예상하지 못한 서버 오류용 (팀장 확인 필요)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
