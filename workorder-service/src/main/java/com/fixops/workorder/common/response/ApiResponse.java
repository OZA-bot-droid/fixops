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
    public static ApiResponse<Void> error(ErrorCode errorCode, String traceId) {
        return new ApiResponse<>(false, null, new ApiError(errorCode.getCode(), errorCode.getMessage()), traceId, Instant.now());
    }

    // 에러 메시지를 바꿔 전달할 때
    public static ApiResponse<Void> error(ErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(false, null, new ApiError(errorCode.getCode(), message), traceId, Instant.now());
    }

    @Getter
    @AllArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
    }
}
