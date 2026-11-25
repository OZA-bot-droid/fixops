package com.fixops.member.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.source.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

import java.security.interfaces.*;
import java.util.*;

@Configuration
public class MemberSecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    RSAPrivateKey signingKey(@Value("${fixops.jwt.private-key-path}") String path) throws Exception {
        return RsaKeys.privateKey(path);
    }

    @Bean
    RSAPublicKey verificationKey(@Value("${fixops.jwt.public-key-path}") String path) throws Exception {
        return RsaKeys.publicKey(path);
    }

    @Bean
    JwtEncoder jwtEncoder(RSAPrivateKey signingKey, RSAPublicKey verificationKey) {
        RSAKey key = new RSAKey.Builder(verificationKey).privateKey(signingKey).keyID("fixops-key-1").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey verificationKey, @Value("${fixops.jwt.issuer}") String issuer, @Value("${fixops.jwt.audience}") String audience, com.fixops.member.service.AuthSessionCache sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(verificationKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            var a = issuerValidator.validate(jwt);
            if (a.hasErrors()) return a;
            if (!jwt.getAudience().contains(audience))
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid audience", null));
            String sid = jwt.getClaimAsString("sid");
            try {
                if (sid != null && jwt.getSubject() != null &&
                        sessions.isActive(java.util.UUID.fromString(sid), Long.valueOf(jwt.getSubject())))
                    return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException invalidSessionId) {
                // Invalid session ID / subject is always rejected.
            }
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Session is not active", null));
        });
        return decoder;
    }

    @Bean
    SecurityFilterChain filter(
            HttpSecurity http,
            @Value("${fixops.cookie.allowed-origins}")
            String allowedOrigins
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(
                        new AuthOriginFilter(allowedOrigins),
                        CsrfFilter.class
                )
                .sessionManagement(s ->
                        s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                HttpMethod.POST,
                                "/members/signup",
                                "/members/login",
                                "/members/refresh",
                                "/members/clear-cookie"
                        ).permitAll()

                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o ->
                        o.jwt(j -> {})
                )
                .build();
    }
}
