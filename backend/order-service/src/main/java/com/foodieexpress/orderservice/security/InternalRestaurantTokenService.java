package com.foodieexpress.orderservice.security;

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
public class InternalRestaurantTokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    public InternalRestaurantTokenService(
            @Qualifier("restaurantInternalJwtEncoder") JwtEncoder encoder,
            Duration restaurantInternalTokenTtl,
            @Value("${app.internal.jwt.issuer}") String issuer,
            @Value("${app.internal.jwt.audience}") String audience) {
        this.encoder = encoder;
        this.ttl = restaurantInternalTokenTtl;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issueMenuReadToken() {
        return issue("menu:read");
    }

    public String issueOwnerVerificationToken() {
        return issue("restaurant:ownership:read");
    }

    public String issueDeliveryProvisionToken() {
        return issue("delivery:provision");
    }

    private String issue(String scope) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("order-service")
                .audience(List.of(audience))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("scopes", List.of(scope))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.ES256).build(), claims)).getTokenValue();
    }
}
