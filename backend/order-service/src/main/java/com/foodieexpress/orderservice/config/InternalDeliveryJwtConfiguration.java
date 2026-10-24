package com.foodieexpress.orderservice.config;

import com.foodieexpress.orderservice.security.InternalDeliveryKeyPair;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
public class InternalDeliveryJwtConfiguration {

    private static final Duration MAX_TOKEN_TTL = Duration.ofMinutes(2);
    private static final Set<String> ALLOWED_SCOPES = Set.of("order:delivery-status:write");

    @Bean
    InternalDeliveryKeyPair orderDeliveryKeyPair(
            @Value("${app.internal.delivery.private-key-base64:}") String privateKeyBase64,
            @Value("${app.internal.delivery.public-key-base64:}") String publicKeyBase64) {
        return InternalDeliveryKeyPair.parse(privateKeyBase64, publicKeyBase64);
    }

    @Bean
    org.springframework.security.oauth2.jwt.JwtEncoder orderDeliveryJwtEncoder(
            InternalDeliveryKeyPair orderDeliveryKeyPair) {
        return org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withKeyPair(
                orderDeliveryKeyPair.publicKey(), orderDeliveryKeyPair.privateKey()).build();
    }

    @Bean
    ECPublicKey deliveryServicePublicKey(
            @Value("${app.internal.delivery.callback-public-key-base64:}") String publicKeyBase64) {
        return parsePublicKey(publicKeyBase64);
    }

    @Bean
    JwtDecoder internalDeliveryJwtDecoder(
            ECPublicKey deliveryServicePublicKey,
            @Value("${app.internal.delivery.callback-issuer}") String issuer,
            @Value("${app.internal.delivery.callback-audience}") String audience) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector((header, context) ->
                JWSAlgorithm.ES256.equals(header.getAlgorithm()) ? List.of(deliveryServicePublicKey) : List.of());
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().equals(List.of(audience))
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Internal token audience is invalid");
        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> {
            Object scopesClaim = jwt.getClaims().get("scopes");
            boolean validScopes = scopesClaim instanceof List<?> scopes
                    && scopes.size() == 1
                    && scopes.stream().allMatch(scope -> scope instanceof String value
                            && ALLOWED_SCOPES.contains(value));
            boolean validClaims = "delivery-service".equals(jwt.getSubject())
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
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator, claimsValidator));
        return decoder;
    }

    private ECPublicKey parsePublicKey(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalStateException("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64 is required");
        }
        byte[] encoded = null;
        try {
            encoded = java.util.Base64.getDecoder().decode(publicKeyBase64);
            var key = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
            if (key instanceof ECPublicKey ecKey
                    && ecKey.getParams().getCurve().getField().getFieldSize() == 256) {
                return ecKey;
            }
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64 must be a valid P-256 key");
        } finally {
            if (encoded != null) java.util.Arrays.fill(encoded, (byte) 0);
        }
        throw new IllegalStateException("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64 must be a valid P-256 key");
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
