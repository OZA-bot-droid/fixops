package com.fixops.member.service;

import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final MemberRepository members;
    private final OrganizationMemberRepository memberships;
    private final RefreshSessionRepository sessions;
    private final AuthSessionRepository authSessions;
    private final AuthSessionCache cache;
    private final PasswordEncoder encoder;
    private final TransactionTemplate txTemplate;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom random = new SecureRandom();

    @Value("${fixops.jwt.issuer}")
    private String issuer;
    @Value("${fixops.jwt.audience}")
    private String audience;
    @Value("${fixops.jwt.access-minutes:15}")
    private int accessMinutes;
    @Value("${fixops.jwt.refresh-days:7}")
    private int refreshDays;

    public AuthService(MemberRepository members, OrganizationMemberRepository memberships,
                       RefreshSessionRepository sessions, AuthSessionRepository authSessions,
                       AuthSessionCache cache, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                       TransactionTemplate txTemplate) {
        this.members = members;
        this.memberships = memberships;
        this.sessions = sessions;
        this.authSessions = authSessions;
        this.cache = cache;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.txTemplate = txTemplate;
    }

    @Transactional
    public Member signup(String email, String password, String name) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (members.findByEmail(normalized).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        return members.save(new Member(normalized, encoder.encode(password), name.trim(), false));
    }

    public TokenResult login(String email, String password) {
        Member member = members.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!encoder.matches(password, member.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        // Commit DB state before publishing an active Redis session.
        AuthSession session = new AuthSession(UUID.randomUUID(), member.id,
                Instant.now().plus(Duration.ofDays(refreshDays)));
        TokenResult tokens = txTemplate.execute(status -> {
            authSessions.save(session);
            return issue(member, session);
        });
        // Fail closed if Redis is unavailable: never send tokens with an inactive sid.
        try {
            cache.activate(session.id, member.id, session.expiresAt);
            return tokens;
        } catch (RuntimeException redisFailure) {
            // DB already committed. Compensate persistently so cache rebuild cannot revive it.
            try {
                txTemplate.execute(status -> {
                    AuthSession stored = authSessions.lockById(session.id).orElse(null);
                    if (stored != null) {
                        stored.revoked = true;
                        stored.revokedAt = Instant.now();
                        sessions.revokeByAuthSessionId(stored.id);
                    }
                    return null;
                });
            } catch (RuntimeException compensationFailure) {
                redisFailure.addSuppressed(compensationFailure);
            }
            try {
                cache.revoke(session.id);
            } catch (RuntimeException cleanupFailure) {
                redisFailure.addSuppressed(cleanupFailure);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE", redisFailure);
        }
    }

    public TokenResult refresh(String raw) {
        return refresh(raw, null, null);
    }

    // 의도적으로 @Transactional을 붙이지 않는다.
    // 재사용 탐지 시 DB 회수를 커밋하고 난 뒤 401을 반환해야 한다.
    public TokenResult refresh(String raw, UUID expectedSid, Long expectedMemberId) {
        if ((expectedSid == null) != (expectedMemberId == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        String hash = sha(raw);
        TokenResult result = txTemplate.execute(status -> {
            UUID sid = sessions.findSessionIdByTokenHash(hash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
            AuthSession session = authSessions.lockById(sid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
            // 세션을 먼저 잠그고 실제 토큰 엔티티를 조회한다.
            RefreshSession token = sessions.findByTokenHash(hash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
            if (!session.id.equals(token.authSessionId) || !session.memberId.equals(token.memberId))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
            // 기존 탭의 갱신이 다른 계정/세션의 쿠키를 회전시키는 것을 방지한다.
            // 예상 SID/memberId는 인증 자격 증명이 아니라 추가적인 일치 조건이다.
            if (expectedSid != null && (!expectedSid.equals(session.id) || !expectedMemberId.equals(session.memberId)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
            Instant now = Instant.now();
            if (session.revoked || !session.expiresAt.isAfter(now) || !token.expiresAt.isAfter(now))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
            if (!isCacheActive(session.id, session.memberId))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SESSION_NOT_ACTIVE");
            // 활성 세션에서 이미 사용된(회전된) 토큰을 다시 제출하면 해당 SID만 회수한다.
            // 정상 동시 요청/응답 유실도 재사용으로 판단될 수 있으므로 재로그인 정책을 적용한다.
            if (token.revoked) {
                revokeSession(session);
                return null;
            }
            token.revoked = true;
            sessions.saveAndFlush(token);
            Member member = members.findById(session.memberId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
            return issue(member, session);
        });
        if (result == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED");
        return result;
    }

    @Transactional
    public void logout(Long memberId, UUID sid, String raw) {
        AuthSession session = authSessions.lockById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
        requireMatchingCookie(session, memberId, sid, raw);
        revokeSession(session);
    }

    @Transactional
    public void logoutAll(Long memberId, UUID sid, String raw) {
        // 단일 세션을 먼저 잠근 후 전체 세션을 잠그는 역순 Lock을 피한다.
        List<AuthSession> active = authSessions.lockActiveByMemberId(memberId);
        AuthSession requesting = active.stream().filter(s -> s.id.equals(sid)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
        // 기존 Bearer A + 다른 탭의 Cookie B로 전체 로그아웃/쿠키 삭제 금지.
        requireMatchingCookie(requesting, memberId, sid, raw);
        for (AuthSession session : active) revokeSession(session);
    }

    // Internal authorization-change revocation; never expose as a public endpoint.
    @Transactional
    public void revokeMemberSessions(Long memberId) {
        for (AuthSession session : authSessions.lockActiveByMemberId(memberId)) {
            revokeSession(session);
        }
    }

    private void requireMatchingCookie(AuthSession session, Long memberId, UUID sid, String raw) {
        if (!session.memberId.equals(memberId) || !session.id.equals(sid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
        if (session.revoked || !session.expiresAt.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION");
        if (raw == null || raw.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_COOKIE_MISSING");
        // 다른 SID의 토큰 행을 잠그지 않기 위해 스칼라 조회로 먼저 확인한다.
        UUID cookieSid = sessions.findSessionIdByTokenHash(sha(raw))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (!sid.equals(cookieSid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
        RefreshSession token = sessions.findByTokenHash(sha(raw))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (!memberId.equals(token.memberId) || !sid.equals(token.authSessionId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
        if (!token.expiresAt.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        // 회수 요청은 같은 SID의 회전된 토큰도 허용한다. 유효 JWT + 같은 SID가 필요하며
        // 새로운 토큰을 발급하지 않는다. 타 세션의 Cookie는 절대 삭제하지 않는다.
    }

    private boolean isCacheActive(UUID sid, Long memberId) {
        try {
            return cache.isActive(sid, memberId);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE", ex);
        }
    }

    private void revokeSession(AuthSession session) {
        // Redis 접근 실패만 503으로 분류한다. DB 예외를 숨기지 않는다.
        try {
            cache.revoke(session.id);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE", ex);
        }
        session.revoked = true;
        session.revokedAt = Instant.now();
        authSessions.save(session);
        sessions.revokeByAuthSessionId(session.id);
    }

    private TokenResult issue(Member member, AuthSession session) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(Duration.ofMinutes(accessMinutes));
        if (accessExpiry.isAfter(session.expiresAt)) accessExpiry = session.expiresAt;
        long expiresIn = Duration.between(now, accessExpiry).getSeconds();
        if (expiresIn <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED");
        var orgs = memberships.findActiveScopes(member.id);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).issuedAt(now)
                .expiresAt(accessExpiry)
                .subject(member.id.toString()).claim("sid", session.id.toString())
                .claim("email", member.email).claim("platform_admin", member.platformAdmin)
                .claim("memberships", orgs).build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("fixops-key-1").build(), claims)).getTokenValue();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiry = now.plus(Duration.ofDays(refreshDays));
        if (refreshExpiry.isAfter(session.expiresAt)) refreshExpiry = session.expiresAt;
        sessions.save(new RefreshSession(UUID.randomUUID(), session.id, member.id, sha(refresh), refreshExpiry));
        return new TokenResult(access, refresh, expiresIn, refreshExpiry, session.id,
                Map.of("id", member.id, "email", member.email, "name", member.displayName,
                        "platformAdmin", member.platformAdmin, "emailVerified", member.emailVerified, "memberships", orgs));
    }

    @Transactional
    public Map<String,Object> currentMember(long id) {
        Member member=members.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_SESSION"));
        var active=memberships.findActiveScopes(id);
        return Map.of("id",member.id,"email",member.email,"name",member.displayName,
            "platformAdmin",member.platformAdmin,"emailVerified",member.emailVerified,"memberships",active);
    }

    private String sha(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenResult(String accessToken, String refreshToken, long expiresIn,
                              Instant refreshExpiresAt, UUID sessionId, Map<String, Object> member) {
        public Map<String, Object> publicBody() {
            return Map.of("accessToken", accessToken, "tokenType", "Bearer", "expiresIn", expiresIn,
                    "sessionId", sessionId, "member", member);
        }
    }
}
