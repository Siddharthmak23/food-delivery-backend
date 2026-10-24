package com.foodieexpress.deliveryservice.config;

import com.foodieexpress.deliveryservice.security.DeliveryKeyPair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class InternalDeliveryJwtConfiguration {

    @Bean
    DeliveryKeyPair deliveryKeyPair(
            @Value("${app.internal.delivery.private-key-base64:}") String privateKeyBase64,
            @Value("${app.internal.delivery.public-key-base64:}") String publicKeyBase64) {
        return DeliveryKeyPair.parse(privateKeyBase64, publicKeyBase64);
    }

    @Bean
    @Qualifier("deliveryInternalJwtEncoder")
    JwtEncoder deliveryInternalJwtEncoder(DeliveryKeyPair deliveryKeyPair) {
        return NimbusJwtEncoder.withKeyPair(deliveryKeyPair.publicKey(), deliveryKeyPair.privateKey()).build();
    }
}
