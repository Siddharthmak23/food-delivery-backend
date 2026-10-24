package com.foodieexpress.restaurantservice.config;

import com.foodieexpress.restaurantservice.security.InternalServicePublicKey;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.security.interfaces.ECPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class InternalJwtConfiguration {

    private static final Set<String> ALLOWED_SCOPES =
            Set.of("menu:read", "restaurant:ownership:read", "delivery:provision");
    private static final Duration MAX_TOKEN_TTL = Duration.ofMinutes(2);

    @Bean
    ECPublicKey internalServicePublicKey(
            @Value("${app.internal.jwt.public-key-base64:}") String publicKeyBase64) {
        return InternalServicePublicKey.parse(publicKeyBase64);
    }

    @Bean
    JwtDecoder internalServiceJwtDecoder(
            ECPublicKey internalServicePublicKey,
            @Value("${app.internal.jwt.issuer}") String issuer,
            @Value("${app.internal.jwt.audience}") String audience) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector((header, context) ->
                JWSAlgorithm.ES256.equals(header.getAlgorithm()) ? List.of(internalServicePublicKey) : List.of());
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().equals(List.of(audience))
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Internal token audience is invalid");
        OAuth2TokenValidator<Jwt> internalClaimsValidator = jwt -> {
            Object scopesClaim = jwt.getClaims().get("scopes");
            boolean validScopes = scopesClaim instanceof List<?> scopes
                    && scopes.size() == 1
                    && scopes.stream().allMatch(scope -> scope instanceof String value
                            && ALLOWED_SCOPES.contains(value));
            boolean validClaims = "order-service".equals(jwt.getSubject())
                    && jwt.getIssuedAt() != null
                    && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                    && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(MAX_TOKEN_TTL) <= 0
                    && validScopes;
            return validClaims
                    ? OAuth2TokenValidatorResult.success()
                    : failure("Internal token identity or scope is invalid");
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator,
                internalClaimsValidator));
        return decoder;
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
