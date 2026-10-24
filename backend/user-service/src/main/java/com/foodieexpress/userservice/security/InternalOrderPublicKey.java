package com.foodieexpress.userservice.security;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class InternalOrderPublicKey {

    private InternalOrderPublicKey() {
    }

    public static ECPublicKey parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalidKey();
        }
        try {
            var key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (!(key instanceof ECPublicKey ecKey)
                    || ecKey.getParams().getCurve().getField().getFieldSize() != 256
                    || ecKey.getParams().getOrder().bitLength() != 256) {
                throw invalidKey();
            }
            return ecKey;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw invalidKey();
        }
    }

    private static IllegalStateException invalidKey() {
        return new IllegalStateException(
                "ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64 must be a valid P-256 public key");
    }
}
