package com.fixops.member.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.*;

@RestControllerAdvice
public class Errors {
    private static final Logger log=LoggerFactory.getLogger(Errors.class);
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> exception(ResponseStatusException ex,HttpServletRequest request){
        String code=ex.getReason();
        if(code==null)code=switch(ex.getStatusCode().value()){
            case 400 -> "INVALID_INPUT";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "ORG_ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "INVALID_STATE_TRANSITION";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 422 -> "ROSTER_VALIDATION_FAILED";
            case 429 -> "RATE_LIMITED";
            case 500 -> "INTERNAL_ERROR";
            case 503 -> "DEPENDENCY_UNAVAILABLE";
            default -> "REQUEST_FAILED";
        };
        if(!code.matches("[A-Z][A-Z0-9_]{1,79}"))code="REQUEST_FAILED";
        return response(ex.getStatusCode(),code,"요청을 처리할 수 없습니다.",request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,
        HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception ex,HttpServletRequest request){
        return response(HttpStatus.BAD_REQUEST,"INVALID_INPUT","입력 값을 확인해 주세요.",request);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,Object>> uploadLimit(MaxUploadSizeExceededException ex,HttpServletRequest request){
        return response(HttpStatus.PAYLOAD_TOO_LARGE,"PAYLOAD_TOO_LARGE","파일 크기 제한을 초과했습니다.",request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(NoResourceFoundException ex,HttpServletRequest request){
        return response(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","요청한 리소스를 찾을 수 없습니다.",request);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> method(HttpRequestMethodNotSupportedException ex,HttpServletRequest request){
        return response(HttpStatus.METHOD_NOT_ALLOWED,"REQUEST_FAILED","지원하지 않는 요청 방식입니다.",request);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> media(HttpMediaTypeNotSupportedException ex,HttpServletRequest request){
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"REQUEST_FAILED","지원하지 않는 요청 형식입니다.",request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception ex,HttpServletRequest request){
        // Do not include exception messages, request bodies, or stack traces containing resident data.
        log.error("Unhandled Member request failure: {}",ex.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","서버 오류가 발생했습니다.",request);
    }
    private ResponseEntity<Map<String,Object>> response(HttpStatusCode status,String code,String message,HttpServletRequest request){
        String trace=request.getHeader("X-Request-Id");
        if(trace==null||!trace.matches("[A-Za-z0-9._-]{1,128}"))trace=UUID.randomUUID().toString();
        var builder=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if(status.value()==401)builder.header(HttpHeaders.WWW_AUTHENTICATE,"Bearer");
        return builder.body(Map.of("success",false,"error",Map.of("code",code,"message",message),
            "traceId",trace,"timestamp",Instant.now().toString()));
    }
}
