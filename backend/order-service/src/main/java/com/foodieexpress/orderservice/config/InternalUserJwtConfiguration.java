package com.foodieexpress.orderservice.config;

import com.foodieexpress.orderservice.security.InternalUserKeyPair;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class InternalUserJwtConfiguration {

    @Bean
    InternalUserKeyPair userInternalKeyPair(
            @Value("${app.internal.user.private-key-base64:}") String privateKeyBase64,
            @Value("${app.internal.user.public-key-base64:}") String publicKeyBase64) {
        return InternalUserKeyPair.parse(privateKeyBase64, publicKeyBase64);
    }

    @Bean
    @Qualifier("userInternalJwtEncoder")
    JwtEncoder userInternalJwtEncoder(InternalUserKeyPair userInternalKeyPair) {
        return NimbusJwtEncoder.withKeyPair(
                        userInternalKeyPair.publicKey(), userInternalKeyPair.privateKey())
                .build();
    }
}
