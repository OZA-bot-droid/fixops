package com.fixops.workorder.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // 값이 null인 필드를 JSON 응답에서 제외
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ApiError error;
    private String traceId;
    private Instant timestamp;

    // 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    // 실패 응답
    public static ApiResponse<Void> error(String code, String message, String traceId) {
        return new ApiResponse<>(false, null, new ApiError(code, message), traceId, Instant.now());
    }

    @Getter
    @AllArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
    }
}
