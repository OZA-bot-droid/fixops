package com.fixops.member.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class AuthOriginFilter
        extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    private static final Set<String> AUTH_PATHS =
            Set.of(
                    "/members/login",
                    "/members/signup",
                    "/members/refresh",
                    "/members/logout",
                    "/members/logout-all",
                    "/members/clear-cookie"
            );

    public AuthOriginFilter(String origins) {

        this.allowedOrigins =
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !"POST".equals(request.getMethod())
                || !AUTH_PATHS.contains(
                request.getServletPath()
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String origin = request.getHeader("Origin");

        String csrfHeader =
                request.getHeader("X-FixOps-CSRF");

        if (origin == null ||
                !allowedOrigins.contains(origin) ||
                !"1".equals(csrfHeader)) {

            response.setStatus(403);
            response.setContentType(
                    "application/json;charset=UTF-8"
            );

            response.getWriter().write("""
                    {
                      "success": false,
                      "error": {
                        "code": "CSRF_REJECTED",
                        "message": "허용되지 않은 인증 요청입니다."
                      }
                    }
                    """);

            return;
        }

        chain.doFilter(request, response);
    }
}