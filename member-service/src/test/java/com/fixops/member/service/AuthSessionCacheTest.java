package com.fixops.member.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthSessionCacheTest {
    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String,String> values = mock(ValueOperations.class);
    private final AuthSessionCache cache = new AuthSessionCache(redis);

    @Test
    void activeSessionMatchesMember() {
        UUID sid=UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fixops:auth:active:"+sid)).thenReturn("7");
        assertTrue(cache.isActive(sid,7L));
        assertFalse(cache.isActive(sid,8L));
    }

    @Test
    void expiredSessionCannotBeActivated() {
        assertThrows(IllegalStateException.class,
            () -> cache.activate(UUID.randomUUID(),1L,Instant.now().minusSeconds(1)));
        verifyNoInteractions(redis);
    }

    @Test
    void activationUsesRemainingSessionLifetime() {
        UUID sid=UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(values);
        cache.activate(sid,5L,Instant.now().plusSeconds(60));
        verify(values).set(eq("fixops:auth:active:"+sid),eq("5"),
            argThat((Duration ttl)->ttl.toMillis()>0 && ttl.toSeconds()<=60));
    }

    @Test
    void revocationRemovesEligibilityKey() {
        UUID sid=UUID.randomUUID();
        cache.revoke(sid);
        verify(redis).delete("fixops:auth:active:"+sid);
    }
}
