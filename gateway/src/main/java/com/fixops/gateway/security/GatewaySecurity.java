package com.fixops.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class GatewaySecurity {
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${fixops.jwt.public-key-path}") String path,
                                 @Value("${fixops.jwt.issuer}") String issuer,
                                 @Value("${fixops.jwt.audience}") String audience) throws Exception {
        RSAPublicKey key = RsaKeys.publicKey(path);
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            var result = issuerValidator.validate(jwt);
            if (result.hasErrors()) return result;
            return jwt.getAudience()!=null && jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        });
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, SessionAuthorizationManager manager) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, ex) ->
                error(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다.");
        ServerAccessDeniedHandler deniedHandler = (exchange, ex) -> {
            String code = exchange.getAttribute(SessionAuthorizationManager.SESSION_ERROR);
            if ("SESSION_STORE_UNAVAILABLE".equals(code))
                return error(exchange, HttpStatus.SERVICE_UNAVAILABLE, code, "인증 세션 저장소에 연결할 수 없습니다.");
            if ("SESSION_NOT_ACTIVE".equals(code))
                return error(exchange, HttpStatus.UNAUTHORIZED, code, "활성 인증 세션이 없습니다.");
            if ("UNAUTHENTICATED".equals(code))
                return error(exchange, HttpStatus.UNAUTHORIZED, code, "인증이 필요합니다.");
            return error(exchange, HttpStatus.FORBIDDEN, "ORG_ACCESS_DENIED", "접근할 수 없습니다.");
        };
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.pathMatchers(HttpMethod.POST,
                                "/api/members/signup", "/api/members/login", "/api/members/refresh", "/api/members/clear-cookie").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyExchange().access(manager))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(o -> o.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler).jwt(j -> {}))
                .build();
    }

    // code/message는 위의 고정된 상수만 전달한다. 토큰/예외 메시지를 응답에 노출하지 않는다.
    private static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        if (status == HttpStatus.UNAUTHORIZED) response.getHeaders().set("WWW-Authenticate", "Bearer");
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (traceId == null || !traceId.matches("[A-Za-z0-9._-]{1,128}")) traceId = UUID.randomUUID().toString();
        byte[] body = ("{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"},\"traceId\":\"%s\",\"timestamp\":\"%s\"}")
                .formatted(code, message, traceId, Instant.now()).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
