package com.foodieexpress.deliveryservice.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class OrderServicePublicKey {

    private OrderServicePublicKey() {}

    public static ECPublicKey parse(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw invalidKey();
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
            try {
                var key = KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(encoded));
                if (key instanceof ECPublicKey ecKey && ecKey.getParams().getCurve().getField().getFieldSize() == 256) {
                    return ecKey;
                }
            } finally {
                java.util.Arrays.fill(encoded, (byte) 0);
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw invalidKey();
        }
        throw invalidKey();
    }

    private static IllegalStateException invalidKey() {
        return new IllegalStateException(
                "ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64 must be a valid P-256 public key");
    }
}
