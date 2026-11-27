package com.fixops.member.service;

import com.fixops.member.model.AuthSession;
import com.fixops.member.model.RefreshSession;
import com.fixops.member.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {
    private final UUID sid = UUID.randomUUID();
    private final UUID otherSid = UUID.randomUUID();
    private final Long memberId = 10L;
    private final MemberRepository members = mock(MemberRepository.class);
    private final OrganizationMemberRepository memberships = mock(OrganizationMemberRepository.class);
    private final RefreshSessionRepository tokens = mock(RefreshSessionRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AuthSessionCache cache = mock(AuthSessionCache.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final JwtEncoder encoder = mock(JwtEncoder.class);
    private AuthSession session;
    private RefreshSession token;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        Instant expiry = Instant.now().plusSeconds(3600);
        session = new AuthSession(sid, memberId, expiry);
        token = new RefreshSession(UUID.randomUUID(), sid, memberId, "not-a-real-token-hash", expiry);
        when(tx.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(tokens.findSessionIdByTokenHash(anyString())).thenReturn(Optional.of(sid));
        when(sessions.lockById(sid)).thenReturn(Optional.of(session));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(cache.isActive(sid, memberId)).thenReturn(true);
        auth = new AuthService(members, memberships, tokens, sessions, cache,
                mock(PasswordEncoder.class), encoder, new TransactionTemplate(tx));
    }

    @Test
    void expectedSessionMismatchDoesNotRotateOrRevoke() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.refresh("test", otherSid, memberId));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("SESSION_SCOPE_DENIED", error.getReason());
        assertFalse(token.revoked);
        verify(tokens, never()).saveAndFlush(any());
        verifyNoInteractions(cache, encoder);
    }

    @Test
    void replayRevokesOnlyMatchingSessionAndCommitsBeforeError() {
        token.revoked = true;
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.refresh("test", sid, memberId));
        assertEquals("REFRESH_TOKEN_REUSED", error.getReason());
        assertTrue(session.revoked);
        var order = inOrder(cache, sessions, tokens, tx);
        order.verify(cache).revoke(sid);
        order.verify(sessions).save(session);
        order.verify(tokens).revokeByAuthSessionId(sid);
        order.verify(tx).commit(any());
        verify(tx, never()).rollback(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void redisLookupFailureIsNotInvalidToken() {
        when(cache.isActive(sid, memberId)).thenThrow(new RedisConnectionFailureException("test outage"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> auth.refresh("test"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals("SESSION_STORE_UNAVAILABLE", error.getReason());
        assertFalse(token.revoked);
        verify(tokens, never()).saveAndFlush(any());
    }

    @Test
    void logoutAllRejectsAnotherCookieBeforeRevoking() {
        when(sessions.lockActiveByMemberId(memberId)).thenReturn(List.of(session));
        when(tokens.findSessionIdByTokenHash(anyString())).thenReturn(Optional.of(otherSid));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.logoutAll(memberId, sid, "test"));
        assertEquals("SESSION_SCOPE_DENIED", error.getReason());
        assertFalse(session.revoked);
        verify(cache, never()).revoke(any());
        verify(tokens, never()).revokeByAuthSessionId(any());
    }

    @Test
    void logoutAllRevokesMatchingMembersSessions() {
        AuthSession second = new AuthSession(otherSid, memberId, session.expiresAt);
        when(sessions.lockActiveByMemberId(memberId)).thenReturn(List.of(session, second));
        auth.logoutAll(memberId, sid, "test");
        assertTrue(session.revoked);
        assertTrue(second.revoked);
        verify(cache).revoke(sid);
        verify(cache).revoke(otherSid);
        verify(tokens).revokeByAuthSessionId(sid);
        verify(tokens).revokeByAuthSessionId(otherSid);
    }
}
