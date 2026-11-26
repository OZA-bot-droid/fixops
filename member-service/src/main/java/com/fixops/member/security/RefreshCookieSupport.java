package com.fixops.member.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieSupport {

    public static final String COOKIE_NAME =
            "fixops_refresh";

    private static final String COOKIE_PATH =
            "/api/members";

    @Value("${fixops.cookie.secure:false}")
    private boolean secure;

    public ResponseCookie create(
            String refreshToken,
            Instant expiresAt
    ) {
        Duration remaining =
                Duration.between(
                        Instant.now(),
                        expiresAt
                );

        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }

        return ResponseCookie
                .from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(remaining)
                .build();
    }

    public ResponseCookie clear() {

        return ResponseCookie
                .from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}