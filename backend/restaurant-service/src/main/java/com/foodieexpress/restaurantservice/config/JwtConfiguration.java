package com.foodieexpress.restaurantservice.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret-base64:}") String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw invalidSecret();
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw invalidSecret();
        }
        try {
            if (keyBytes.length < 32) {
                throw invalidSecret();
            }
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "Token audience is invalid", null));
        OAuth2TokenValidator<Jwt> requiredClaimsValidator = jwt -> {
            Object rolesClaim = jwt.getClaims().get("roles");
            boolean validRoles = rolesClaim instanceof List<?> roles
                    && !roles.isEmpty()
                    && roles.stream().allMatch(role -> role instanceof String value
                            && Set.of("CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_OPERATOR", "ADMIN").contains(value));
            boolean validClaims = jwt.getSubject() != null
                    && !jwt.getSubject().isBlank()
                    && jwt.getIssuedAt() != null
                    && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && validRoles;
            return validClaims
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "Token is missing required identity claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator,
                requiredClaimsValidator));
        return decoder;
    }

    private IllegalStateException invalidSecret() {
        return new IllegalStateException("JWT_SECRET must be Base64-encoded random data of at least 32 bytes");
    }
}
