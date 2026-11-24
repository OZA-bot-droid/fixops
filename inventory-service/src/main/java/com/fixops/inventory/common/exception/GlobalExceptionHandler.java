package com.fixops.inventory.common.exception;

import com.fixops.inventory.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 오류 처리기. 어느 Controller 에서 예외가 나든 여기로 모여서, 계약서 모양의 실패 응답으로 바뀐다.
 *
 * [동작 방식]
 *   Controller → Service 에서 예외가 던져지면 Spring 이 이 클래스에서
 *   "그 예외 종류를 맡은 @ExceptionHandler" 를 찾아 실행한다.
 *   여러 개가 맞으면 가장 구체적인(자식) 클래스를 맡은 메서드가 선택된다.
 *     예) DuplicateSkuException → handleDuplicateSku  (마지막 handleUnexpected 가 아님)
 *
 * [원칙]
 *   - HTTP 상태와 오류 코드는 docs/04 를 따른다 (ErrorCode).
 *   - 화면에는 정해진 문구만 보낸다. 내부 메시지(SQL, partId=...)는 로그에만 남긴다.
 *   - 4xx(요청자 잘못)는 짧게 warn, 5xx(서버 잘못)는 원인까지 error 로 기록한다.
 *   - traceId 를 응답과 로그에 같이 남겨서, 화면에서 받은 번호로 로그를 찾을 수 있게 한다.
 *     (분산 추적이 연결되면 그 traceId 로 바꾼다. 지금은 요청마다 새로 만든다.)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===== 1. 우리가 만든 업무 예외 =====

    // 401: 로그인 정보(헤더) 없음
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException e) {
        return respond(ErrorCode.UNAUTHENTICATED, null, e);
    }

    // 403: 역할 부족 (예: TECHNICIAN 이 부품 등록)
    @ExceptionHandler(OrgAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrgAccessDeniedException e) {
        return respond(ErrorCode.ORG_ACCESS_DENIED, null, e);
    }

    // 404: 부품 없음, 재고 행 없음 (예외 두 개를 한 메서드에서 처리)
    @ExceptionHandler({PartNotFoundException.class, StockNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, null, e);
    }

    // 409: 재고 부족
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException e) {
        return respond(ErrorCode.INSUFFICIENT_STOCK, null, e);
    }

    // 409: 같은 멱등 키로 다른 내용
    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReuse(IdempotencyKeyReuseException e) {
        return respond(ErrorCode.IDEMPOTENCY_KEY_REUSE, null, e);
    }

    /**
     * 409: SKU 중복 (DUPLICATE_SKU). 기존 부품 번호와 상태를 details 에 담아
     * 화면이 "숨겨진 같은 부품이 있습니다" 같은 안내를 할 수 있게 한다.
     */
    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSku(DuplicateSkuException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (e.getExistingPartId() != null) {
            details.put("partId", e.getExistingPartId());
            details.put("partStatus", e.getExistingStatus());
        }
        return respond(ErrorCode.DUPLICATE_SKU, details, e);
    }

    // ===== 2. 입력값 오류 (400) =====

    /**
     * 본문 검증 실패 (@Valid @RequestBody). 어떤 칸이 왜 틀렸는지 details 에 담는다.
     *   details: {"sku": "공백일 수 없습니다", "name": "..."}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> details.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return respond(ErrorCode.INVALID_INPUT, details, e);
    }

    /**
     * URL 파라미터·헤더 검증 실패 (@Min, @Max, @Size 를 @RequestParam 등에 붙인 경우).
     * Spring 버전과 설정에 따라 둘 중 하나로 들어와서 둘 다 받는다.
     */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleParamValidation(Exception e) {
        return respond(ErrorCode.INVALID_INPUT, null, e);
    }

    /**
     * 형식이 틀린 입력.
     *   - MethodArgumentTypeMismatchException : ?page=abc, ?status=WRONG
     *   - HttpMessageNotReadableException     : 본문 JSON 이 깨졌거나 safetyStock 에 글자
     *   - IllegalArgumentException            : Command 의 검증, 정렬 이름 오류 (우리 코드가 던짐)
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception e) {
        return respond(ErrorCode.INVALID_INPUT, null, e);
    }

    // ===== 3. DB 오류 =====

    // DB 제약 위반 (CHECK quantity >= 0 등). 요청이 규칙에 어긋난 경우라 409 로 본다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        return respond(HttpStatusCode.valueOf(409), ErrorCode.INVALID_INPUT.name(),
                "데이터 규칙에 맞지 않는 요청입니다.", null, e);
    }

    // DB 연결 실패 등 (docs/04: 내부 DB 의존성 장애 → 503)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        return respond(ErrorCode.DEPENDENCY_UNAVAILABLE, null, e);
    }

    // ===== 4. 나머지 전부 =====

    /**
     * 위에서 못 잡은 예외.
     * Spring 이 기본으로 던지는 예외 중 상태를 아는 것(없는 주소 404, 헤더 누락 400, 잘못된 메서드 405)은
     * 그 상태를 그대로 쓰고, 정말 모르는 예외만 500 으로 보낸다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode code = status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.INVALID_INPUT;
            return respond(status, code.name(), code.defaultMessage(), null, e);
        }
        return respond(ErrorCode.INTERNAL_ERROR, null, e);
    }

    // ===== 공통 =====

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, Map<String, Object> details, Exception e) {
        return respond(code.status(), code.name(), code.defaultMessage(), details, e);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatusCode status, String code, String message,
                                                  Map<String, Object> details, Exception e) {
        String traceId = UUID.randomUUID().toString();
        if (status.is5xxServerError()) {
            log.error("[{}] {} {}", traceId, code, e.getMessage(), e);   // 원인(스택)까지 기록
        } else {
            log.warn("[{}] {} {}", traceId, code, e.getMessage());
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, details, traceId));
    }
}
