package com.fixops.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

import java.security.interfaces.RSAPublicKey;

@Configuration
public class GatewaySecurity {
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${fixops.jwt.public-key-path}") String path, @Value("${fixops.jwt.issuer}") String issuer, @Value("${fixops.jwt.audience}") String audience) throws Exception {
        RSAPublicKey key = RsaKeys.publicKey(path);
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            var result = issuerValidator.validate(jwt);
            if (result.hasErrors()) return result;
            return jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        });
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, SessionAuthorizationManager manager) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.pathMatchers(
                                HttpMethod.POST,
                                "/api/members/signup",
                                "/api/members/login",
                                "/api/members/refresh",
                                "/api/members/clear-cookie"
                        ).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll().anyExchange().access(manager))
                .oauth2ResourceServer(o -> o.jwt(j -> {
                })).build();
    }
}
