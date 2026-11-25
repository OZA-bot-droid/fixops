package com.fixops.member.web;

import com.fixops.member.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fixops.member.security.RefreshCookieSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.*;

@RestController
@RequestMapping("/members")
public class AuthController {
    private final AuthService auth;
    private final RefreshCookieSupport cookies;

    public AuthController(AuthService auth, RefreshCookieSupport cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    public record Signup(@Email @NotBlank String email, @Size(min = 12, max = 100) String password,
                         @NotBlank @Size(max = 100) String name) {
    }

    public record Login(@Email @NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@Valid @RequestBody Signup req) {
        var m = auth.signup(req.email(), req.password(), req.name());
        return Api.ok(Map.of("id", m.id, "email", m.email, "emailVerified", m.emailVerified));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody Login req
    ) {
        var tokens = auth.login(
                req.email(),
                req.password()
        );
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.create(
                                tokens.refreshToken(),
                                tokens.refreshExpiresAt()
                        ).toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .body(Api.ok(tokens.publicBody()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @CookieValue(
                    name = RefreshCookieSupport.COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        if (refreshToken == null ||
                refreshToken.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_COOKIE_MISSING"
            );
        }
        var tokens = auth.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.create(
                                tokens.refreshToken(),
                                tokens.refreshExpiresAt()
                        ).toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .body(Api.ok(tokens.publicBody()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(
                    name = RefreshCookieSupport.COOKIE_NAME,
                    required = false
            ) String refreshToken,

            JwtAuthenticationToken user
    ) {
        if (refreshToken == null ||
                refreshToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN"
            );
        }
        auth.logout(
                Long.valueOf(user.getName()),
                UUID.fromString(
                        user.getToken()
                                .getClaimAsString("sid")
                ),
                refreshToken
        );
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.clear().toString()
                )
                .body(Api.ok(
                        Map.of("loggedOut", true)
                ));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Object>> logoutAll(
            JwtAuthenticationToken user
    ) {
        auth.logoutAll(
                Long.valueOf(user.getName())
        );
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.clear().toString()
                )
                .body(Api.ok(
                        Map.of("loggedOutAll", true)
                ));
    }

    @GetMapping("/me")
    public Map<String, Object> me(JwtAuthenticationToken user) {
        return Api.ok(Map.of("id", Long.parseLong(user.getName()), "email", user.getToken().getClaimAsString("email"), "platformAdmin", Boolean.TRUE.equals(user.getToken().getClaim("platform_admin")), "memberships", user.getToken().getClaim("memberships")));
    }

    @PostMapping("/clear-cookie")
    public ResponseEntity<Map<String, Object>> clearCookie() {

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.clear().toString()
                )
                .body(Api.ok(
                        Map.of("cookieCleared", true)
                ));
    }
}
