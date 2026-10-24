package com.foodieexpress.orderservice.config;

import com.foodieexpress.orderservice.security.InternalRestaurantKeyPair;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class InternalRestaurantJwtConfiguration {

    @Bean
    InternalRestaurantKeyPair restaurantInternalKeyPair(
            @Value("${app.internal.restaurant.private-key-base64:}") String privateKeyBase64,
            @Value("${app.internal.restaurant.public-key-base64:}") String publicKeyBase64) {
        return InternalRestaurantKeyPair.parse(privateKeyBase64, publicKeyBase64);
    }

    @Bean
    @Qualifier("restaurantInternalJwtEncoder")
    JwtEncoder restaurantInternalJwtEncoder(InternalRestaurantKeyPair restaurantInternalKeyPair) {
        return NimbusJwtEncoder.withKeyPair(
                        restaurantInternalKeyPair.publicKey(), restaurantInternalKeyPair.privateKey())
                .build();
    }

    @Bean
    Duration restaurantInternalTokenTtl(
            @Value("${app.internal.jwt.ttl:PT1M}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException("INTERNAL_JWT_TTL must be positive and no longer than PT2M");
        }
        return ttl;
    }
}
