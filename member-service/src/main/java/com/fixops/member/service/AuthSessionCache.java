package com.fixops.member.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthSessionCache {
    private final StringRedisTemplate redis;

    public AuthSessionCache(StringRedisTemplate redis) { this.redis = redis; }

    private String key(UUID sid) { return "fixops:auth:active:" + sid; }

    public void activate(UUID sid, Long memberId, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalStateException("Expired session");
        redis.opsForValue().set(key(sid), memberId.toString(), ttl);
    }

    public boolean isActive(UUID sid, Long memberId) {
        return memberId.toString().equals(redis.opsForValue().get(key(sid)));
    }

    public void revoke(UUID sid) { redis.delete(key(sid)); }
}
