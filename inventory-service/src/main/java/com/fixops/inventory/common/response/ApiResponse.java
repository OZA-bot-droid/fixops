package com.fixops.inventory.common.response;

import java.time.Instant;

/**
 * 성공 응답의 공통 껍데기 (docs/04, OpenAPI SuccessEnvelope).
 *   {"success": true, "data": {...}, "timestamp": "2026-11-24T03:00:00Z"}
 * 모든 API가 같은 모양으로 응답해야 화면과 다른 서비스가 한 가지 방식으로 읽을 수 있다.
 */
public record ApiResponse<T>(boolean success, T data, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, Instant.now());
    }
}
