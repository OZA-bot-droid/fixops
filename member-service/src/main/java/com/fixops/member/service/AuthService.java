package com.fixops.member.service;

import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${fixops.demo.email-verification-bypass:false}")
    private boolean demoVerify;

    public AuthService(MemberRepository members, OrganizationMemberRepository memberships,
                       RefreshSessionRepository sessions, AuthSessionRepository authSessions,
                       AuthSessionCache cache, PasswordEncoder encoder, JwtEncoder jwtEncoder, TransactionTemplate txTemplate) {
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
        return members.save(new Member(normalized, encoder.encode(password), name.trim(), demoVerify));
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
            // A previous Redis write might have succeeded before reporting an error.
            try {
                cache.revoke(session.id);
            } catch (RuntimeException cleanupFailure) {
                redisFailure.addSuppressed(cleanupFailure);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE", redisFailure);
        }
    }

    @Transactional
    public TokenResult refresh(String raw) {
        String hash = sha(raw);
        // Resolve sid without locking the refresh row first; always acquire session lock first.
        RefreshSession initial = sessions.findUnlockedByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        AuthSession session = authSessions.lockById(initial.authSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
        RefreshSession token = sessions.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (!session.id.equals(token.authSessionId) || !session.memberId.equals(token.memberId) ||
                session.revoked || !session.expiresAt.isAfter(Instant.now()) ||
                token.revoked || !token.expiresAt.isAfter(Instant.now()) ||
                !cache.isActive(session.id, session.memberId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        token.revoked = true;
        sessions.saveAndFlush(token);
        Member member = members.findById(session.memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
        return issue(member, session);
    }

    @Transactional
    public void logout(Long memberId, UUID sid, String raw) {
        AuthSession session = authSessions.lockById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));
        if (!session.memberId.equals(memberId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
        RefreshSession token = sessions.findByTokenHash(sha(raw))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (!memberId.equals(token.memberId) || !sid.equals(token.authSessionId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_SCOPE_DENIED");
        // Remove Redis eligibility before confirming persistent revocation (fail closed).
        cache.revoke(sid);
        session.revoked = true;
        session.revokedAt = Instant.now();
        authSessions.save(session);
        sessions.revokeByAuthSessionId(sid);
    }

    @Transactional
    public void logoutAll(Long memberId) {
        List<AuthSession> active = authSessions.lockActiveByMemberId(memberId);
        for (AuthSession session : active) {
            cache.revoke(session.id);
            session.revoked = true;
            session.revokedAt = Instant.now();
            sessions.revokeByAuthSessionId(session.id);
        }
        authSessions.saveAll(active);
    }

    private TokenResult issue(Member member, AuthSession session) {
        Instant now = Instant.now();
        var active = memberships.findByMemberIdAndStatus(member.id, "ACTIVE");
        var orgs = active.stream().map(x -> Map.of("organizationId", x.organizationId, "role", x.role)).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(accessMinutes)))
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
        return new TokenResult(access, refresh,accessMinutes * 60L, refreshExpiry, session.id,
                Map.of("id", member.id, "email", member.email, "name", member.displayName,
                        "platformAdmin", member.platformAdmin, "memberships", orgs));
    }

    private String sha(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenResult(
            String accessToken,
            String refreshToken,
            long expiresIn,
            Instant refreshExpiresAt,
            UUID sessionId,
            Map<String, Object> member
    ) {
        public Map<String, Object> publicBody() {
            return Map.of(
                    "accessToken", accessToken,
                    "tokenType", "Bearer",
                    "expiresIn", expiresIn,
                    "sessionId", sessionId,
                    "member", member
            );
        }
    }
}
