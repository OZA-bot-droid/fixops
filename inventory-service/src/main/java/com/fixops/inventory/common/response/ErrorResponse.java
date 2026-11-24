package com.fixops.inventory.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * 실패 응답의 공통 껍데기 (docs/04, OpenAPI ErrorEnvelope).
 *   {"success": false,
 *    "error": {"code": "INSUFFICIENT_STOCK", "message": "재고가 부족합니다.", "details": {...}},
 *    "traceId": "...", "timestamp": "2026-11-24T03:00:00Z"}
 */
public record ErrorResponse(boolean success, Error error, String traceId, Instant timestamp) {

    /** details 가 없으면 JSON 에서 아예 빼서 보낸다 (null 로 보내지 않음). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Error(String code, String message, Map<String, Object> details) {}

    public static ErrorResponse of(String code, String message, Map<String, Object> details, String traceId) {
        return new ErrorResponse(false, new Error(code, message, details), traceId, Instant.now());
    }
}
