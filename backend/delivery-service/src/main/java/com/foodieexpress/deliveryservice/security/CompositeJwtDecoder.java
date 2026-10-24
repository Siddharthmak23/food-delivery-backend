package com.foodieexpress.deliveryservice.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public class CompositeJwtDecoder implements JwtDecoder {

    private final JwtDecoder userJwtDecoder;
    private final JwtDecoder internalOrderJwtDecoder;

    public CompositeJwtDecoder(JwtDecoder userJwtDecoder, JwtDecoder internalOrderJwtDecoder) {
        this.userJwtDecoder = userJwtDecoder;
        this.internalOrderJwtDecoder = internalOrderJwtDecoder;
    }

    @Override
    public Jwt decode(String token) {
        try {
            var algorithm = SignedJWT.parse(token).getHeader().getAlgorithm();
            if (JWSAlgorithm.HS256.equals(algorithm)) {
                return userJwtDecoder.decode(token);
            }
            if (JWSAlgorithm.ES256.equals(algorithm)) {
                return internalOrderJwtDecoder.decode(token);
            }
        } catch (java.text.ParseException exception) {
            throw new BadJwtException("Malformed token", exception);
        }
        throw new BadJwtException("Unsupported token algorithm");
    }
}
