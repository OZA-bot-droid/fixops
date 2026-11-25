package com.fixops.workorder.common.response;

import org.slf4j.MDC;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

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

        /*
        *
        *   X-Member-Id 으로 동작한다는 기준!!!!!
        *
        *
        * */
        // X-Member-Id가 누락된 경우 401으로 처리
        if ("X-Member-Id".equalsIgnoreCase(ex.getHeaderName())) {
            return errorResponse(ErrorCode.UNAUTHENTICATED);
        }

        // 다른 필수 헤더 누락은 400으로 처리
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

    // JPA 낙관적 락(@Version) 예외를 409로 변환
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        return errorResponse(ErrorCode.RESOURCE_VERSION_CONFLICT);
    }

    // 비관적 잠금 실패 처리
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handlePessimisticLockingFailure(PessimisticLockingFailureException ex) {
        return errorResponse(ErrorCode.RESOURCE_LOCK_CONFLICT);
    }

    // 필수 요청 파라미터 누락 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    // DB 무결성 제약조건 위반 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {

        String constraintName = findConstraintName(e);

        ErrorCode errorCode;


        if ("no_technician_overlap".equals(constraintName) || "uq_active_schedule_per_workorder".equals(constraintName)) {

            errorCode = ErrorCode.BOOKING_CONFLICT;

        } else if ("uq_active_proposal_per_schedule".equals(constraintName)) {

            errorCode = ErrorCode.RESOURCE_VERSION_CONFLICT;

        } else {

            // 예상하지 못한 무결성 오류는 서버 오류로 처리
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<Void>(false, null, new ApiResponse.ApiError("INTERNAL_SERVER_ERROR", "데이터 처리 중 오류가 발생했습니다."), MDC.get("traceId"), java.time.Instant.now()));
        }


        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode, MDC.get("traceId")));
    }

    // 제약조건 이름을 확인
    private String findConstraintName(Throwable throwable) {

        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException e) {

                return e.getConstraintName();
            }

            cause = cause.getCause();
        }

        return null;
    }
}