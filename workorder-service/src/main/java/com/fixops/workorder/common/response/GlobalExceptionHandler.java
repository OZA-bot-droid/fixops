package com.fixops.workorder.common.response;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // DTO 검증 실패: @Valid, @NotNull, @NotBlank 등
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    // 필수 HTTP Header 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    // JSON 문법 오류 또는 요청 본문 변환 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(HttpMessageNotReadableException ex) {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    // 요청 파라미터 타입 변환 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    // 공통 오류 응답 생성
    private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode, MDC.get("traceId")));
    }

    // 업무 오류 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        return errorResponse(ex.getErrorCode());
    }
}