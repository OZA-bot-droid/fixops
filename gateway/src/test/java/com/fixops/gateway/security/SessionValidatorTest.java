package com.fixops.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionValidatorTest {
    @SuppressWarnings("unchecked")
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String,String> operations = mock(ReactiveValueOperations.class);
    private final SessionValidator validator = new SessionValidator(redis);

    private Jwt token(String sid, String subject) {
        return Jwt.withTokenValue("signed-in-tests")
            .header("alg","RS256")
            .subject(subject)
            .claim("sid",sid)
            .build();
    }

    @Test
    void validSessionAndMatchingMemberAreAccepted() {
        String sid=UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("fixops:auth:active:"+sid)).thenReturn(Mono.just("42"));
        assertTrue(validator.isActive(token(sid,"42")).block());
    }

    @Test
    void mismatchedMemberIsRejected() {
        String sid=UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("fixops:auth:active:"+sid)).thenReturn(Mono.just("99"));
        assertFalse(validator.isActive(token(sid,"42")).block());
    }

    @Test
    void revokedOrMissingSessionIsRejected() {
        String sid=UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("fixops:auth:active:"+sid)).thenReturn(Mono.empty());
        assertFalse(validator.isActive(token(sid,"42")).block());
    }

    @Test
    void invalidSessionIdIsRejectedWithoutRedisAccess() {
        assertFalse(validator.isActive(token("not-a-uuid","42")).block());
        verifyNoInteractions(redis);
    }
}
