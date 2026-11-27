package com.fixops.member.security;

import com.fixops.member.service.AuthSessionCache;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class MemberSecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

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
    JwtDecoder jwtDecoder(RSAPublicKey verificationKey, @Value("${fixops.jwt.issuer}") String issuer,
                          @Value("${fixops.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(verificationKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            var a = issuerValidator.validate(jwt);
            if (a.hasErrors()) return a;
            if (jwt.getAudience() == null || !jwt.getAudience().contains(audience))
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    @Bean
    SecurityFilterChain filter(HttpSecurity http, AuthSessionCache sessions,
                               @Value("${fixops.cookie.allowed-origins}") String allowedOrigins) throws Exception {
        AuthenticationEntryPoint entry = (request, response, ex) ->
                AuthSecurityErrors.write(request, response, 401, "UNAUTHENTICATED", "인증이 필요합니다.");
        AccessDeniedHandler denied = (request, response, ex) ->
                AuthSecurityErrors.write(request, response, 403, "ORG_ACCESS_DENIED", "접근할 수 없습니다.");
        return http.csrf(csrf -> csrf.disable())
                .addFilterBefore(new AuthOriginFilter(allowedOrigins), CsrfFilter.class)
                .addFilterAfter(new MemberSessionFilter(sessions), BearerTokenAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/members/signup", "/members/login", "/members/refresh", "/members/clear-cookie").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
                .oauth2ResourceServer(o -> o.authenticationEntryPoint(entry).accessDeniedHandler(denied).jwt(j -> {}))
                .build();
    }
}
