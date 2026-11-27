package com.fixops.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
public class SessionValidator {
    private final ReactiveStringRedisTemplate redis;
    public SessionValidator(ReactiveStringRedisTemplate redis) { this.redis = redis; }

    public Mono<Boolean> isActive(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        if (sid == null || jwt.getSubject() == null) return Mono.just(false);
        try { UUID.fromString(sid); }
        catch (IllegalArgumentException ex) { return Mono.just(false); }
        return redis.opsForValue().get("fixops:auth:active:" + sid)
                .map(jwt.getSubject()::equals).defaultIfEmpty(false);
    }
}
