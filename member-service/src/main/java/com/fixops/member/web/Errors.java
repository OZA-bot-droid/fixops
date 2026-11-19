package com.fixops.member.web;
import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;import java.util.*;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,Object>> exception(ResponseStatusException ex,HttpServletRequest request){
  return ResponseEntity.status(ex.getStatusCode()).body(body(ex.getReason()==null?"REQUEST_FAILED":ex.getReason(),"요청을 처리할 수 없습니다.",request));
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException ex,HttpServletRequest request){
  return ResponseEntity.badRequest().body(body("INVALID_INPUT","입력 값을 확인해 주세요.",request));
 }
 private Map<String,Object> body(String code,String msg,HttpServletRequest request){return Map.of("success",false,"error",Map.of("code",code,"message",msg),"traceId",Optional.ofNullable(request.getHeader("X-Request-Id")).orElse(UUID.randomUUID().toString()),"timestamp",Instant.now().toString());}
}
