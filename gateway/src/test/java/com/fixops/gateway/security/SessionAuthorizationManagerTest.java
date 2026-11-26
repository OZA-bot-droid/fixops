package com.fixops.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionAuthorizationManagerTest {
    @Test
    void inactiveAndRedisOutageAreDifferentDeniedResults() {
        var validator = mock(SessionValidator.class);
        var manager = new SessionAuthorizationManager(validator);
        var jwt = Jwt.withTokenValue("test-only").header("alg", "RS256")
                .subject("10").claim("sid", UUID.randomUUID().toString()).build();
        // 3-argument constructor explicitly marks the token authenticated.
        var authenticated = new JwtAuthenticationToken(jwt, java.util.List.of(), "10");
        var inactive = MockServerWebExchange.from(MockServerHttpRequest.get("/api/members/me"));
        when(validator.isActive(jwt)).thenReturn(Mono.just(false));
        assertFalse(manager.authorize(Mono.just(authenticated), new AuthorizationContext(inactive)).block().isGranted());
        assertEquals("SESSION_NOT_ACTIVE", inactive.getAttribute(SessionAuthorizationManager.SESSION_ERROR));
        var outage = MockServerWebExchange.from(MockServerHttpRequest.get("/api/members/me"));
        when(validator.isActive(jwt)).thenReturn(Mono.error(new RedisConnectionFailureException("test outage")));
        assertFalse(manager.authorize(Mono.just(authenticated), new AuthorizationContext(outage)).block().isGranted());
        assertEquals("SESSION_STORE_UNAVAILABLE", outage.getAttribute(SessionAuthorizationManager.SESSION_ERROR));
    }
}
