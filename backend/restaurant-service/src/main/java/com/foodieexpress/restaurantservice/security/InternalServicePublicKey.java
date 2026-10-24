package com.foodieexpress.restaurantservice.security;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class InternalServicePublicKey {

    private InternalServicePublicKey() {
    }

    public static ECPublicKey parse(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw invalidKey();
        }
        try {
            var key = KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
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
                "ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64 must be a valid P-256 public key");
    }
}
