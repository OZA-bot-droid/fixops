package com.fixops.member.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

final class AuthSecurityErrors {
    private AuthSecurityErrors() {}

    // code/message는 호출하는 보안 필터의 고정 문자열이다.
    static void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null || !traceId.matches("[A-Za-z0-9._-]{1,128}")) traceId = UUID.randomUUID().toString();
        response.getWriter().write(("{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"},\"traceId\":\"%s\",\"timestamp\":\"%s\"}")
                .formatted(code, message, traceId, Instant.now()));
    }
}
