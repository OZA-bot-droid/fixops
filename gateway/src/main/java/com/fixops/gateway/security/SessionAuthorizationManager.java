package com.fixops.gateway.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SessionAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {
    private final SessionValidator sessions;
    public SessionAuthorizationManager(SessionValidator sessions) { this.sessions = sessions; }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication.filter(Authentication::isAuthenticated)
            .flatMap(auth -> {
                if (!(auth.getPrincipal() instanceof Jwt jwt))
                    return Mono.just((AuthorizationResult) new AuthorizationDecision(false));
                return sessions.isActive(jwt).map(active -> (AuthorizationResult) new AuthorizationDecision(active))
                    .onErrorReturn(new AuthorizationDecision(false));
            })
            .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
