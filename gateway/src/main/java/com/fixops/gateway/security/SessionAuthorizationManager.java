package com.fixops.gateway.security;

import org.springframework.dao.DataAccessException;
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
    static final String SESSION_ERROR = SessionAuthorizationManager.class.getName() + ".error";
    private final SessionValidator sessions;
    public SessionAuthorizationManager(SessionValidator sessions) { this.sessions = sessions; }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication.filter(Authentication::isAuthenticated)
                .flatMap(auth -> {
                    if (!(auth.getPrincipal() instanceof Jwt jwt))
                        return Mono.just(denied(context, "UNAUTHENTICATED"));
                    return Mono.defer(() -> sessions.isActive(jwt))
                            .map(active -> active ? (AuthorizationResult) new AuthorizationDecision(true)
                                    : denied(context, "SESSION_NOT_ACTIVE"))
                            // Redis 장애와 정상적인 Key 부재를 구분한다. 어떤 경우에도 업무 서비스로 전달하지 않는다.
                            .onErrorResume(DataAccessException.class,
                                    ex -> Mono.just(denied(context, "SESSION_STORE_UNAVAILABLE")));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> denied(context, "UNAUTHENTICATED")));
    }

    private AuthorizationResult denied(AuthorizationContext context, String code) {
        context.getExchange().getAttributes().put(SESSION_ERROR, code);
        return new AuthorizationDecision(false);
    }
}
