package com.fixops.member.web;

import com.fixops.member.service.AuthService;
import com.fixops.member.security.RefreshCookieSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/members")
public class AuthController {
    private final AuthService auth;
    private final RefreshCookieSupport cookies;
    @org.springframework.beans.factory.annotation.Autowired
    private com.fixops.member.service.RequestRateLimiter limiter;

    public AuthController(AuthService auth, RefreshCookieSupport cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    public record Signup(@Email @NotBlank String email, @NotBlank @Size(min = 12, max = 100) String password,
                         @NotBlank @Size(max = 100) String name) {}
    public record Login(@Email @NotBlank String email, @NotBlank String password) {}
    // 최초 복원은 body 없음. 기존 세션 갱신은 예상 SID/memberId를 함께 보낸다.
    public record RefreshExpectation(UUID sessionId, Long memberId) {}

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@Valid @RequestBody Signup req) {
        limiter.check("signup-global", "all", 120, 60);
        limiter.check("signup-email", req.email().trim().toLowerCase(Locale.ROOT), 3, 3600);
        var m = auth.signup(req.email(), req.password(), req.name());
        return Api.ok(Map.of("id", m.id, "email", m.email, "emailVerified", m.emailVerified));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody Login req) {
        limiter.check("login-global", "all", 300, 60);
        limiter.check("login-email", req.email().trim().toLowerCase(Locale.ROOT), 10, 60);
        return tokenResponse(auth.login(req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @CookieValue(name = RefreshCookieSupport.COOKIE_NAME, required = false) String refreshToken,
            @RequestBody(required = false) RefreshExpectation expected) {
        requireCookie(refreshToken);
        var tokens = auth.refresh(refreshToken, expected == null ? null : expected.sessionId(),
                expected == null ? null : expected.memberId());
        return tokenResponse(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(name = RefreshCookieSupport.COOKIE_NAME, required = false) String refreshToken,
            JwtAuthenticationToken user) {
        requireCookie(refreshToken);
        auth.logout(Long.valueOf(user.getName()), sid(user), refreshToken);
        return cleared(Map.of("loggedOut", true));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Object>> logoutAll(
            @CookieValue(name = RefreshCookieSupport.COOKIE_NAME, required = false) String refreshToken,
            JwtAuthenticationToken user) {
        requireCookie(refreshToken);
        auth.logoutAll(Long.valueOf(user.getName()), sid(user), refreshToken);
        return cleared(Map.of("loggedOutAll", true));
    }

    @GetMapping("/me")
    public Map<String, Object> me(JwtAuthenticationToken user) {
        return Api.ok(auth.currentMember(Long.parseLong(user.getName())));
    }

    @PostMapping("/clear-cookie")
    public ResponseEntity<Map<String, Object>> clearCookie() {
        // 명시적인 브라우저 쿠키 초기화 API. 서버 세션 종료는 보장하지 않는다.
        // 일반 프론트 재로그인 경로에서는 다른 탭의 쿠키 보호를 위해 호출하지 않는다.
        return cleared(Map.of("cookieCleared", true));
    }

    private UUID sid(JwtAuthenticationToken user) {
        return UUID.fromString(user.getToken().getClaimAsString("sid"));
    }

    private void requireCookie(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_COOKIE_MISSING");
    }

    private ResponseEntity<Map<String, Object>> tokenResponse(AuthService.TokenResult tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken(), tokens.refreshExpiresAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Api.ok(tokens.publicBody()));
    }

    private ResponseEntity<Map<String, Object>> cleared(Map<String, Object> data) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Api.ok(data));
    }
}
