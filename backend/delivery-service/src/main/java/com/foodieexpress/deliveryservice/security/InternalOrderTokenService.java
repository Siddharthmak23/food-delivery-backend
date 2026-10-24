package com.foodieexpress.deliveryservice.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class InternalOrderTokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    public InternalOrderTokenService(
            @Qualifier("deliveryInternalJwtEncoder") JwtEncoder encoder,
            @Value("${app.internal.jwt.ttl:PT1M}") Duration ttl,
            @Value("${app.internal.delivery.issuer}") String issuer,
            @Value("${app.internal.delivery.audience}") String audience) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException("INTERNAL_JWT_TTL must be positive and no longer than PT2M");
        }
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issueStatusUpdateToken() {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("delivery-service")
                .audience(List.of(audience))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("scopes", List.of("order:delivery-status:write"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.ES256).build(), claims)).getTokenValue();
    }
}
