package com.foodieexpress.orderservice.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public record InternalDeliveryKeyPair(ECPublicKey publicKey, ECPrivateKey privateKey) {

    public static InternalDeliveryKeyPair parse(String privateBase64, String publicBase64) {
        if (privateBase64 == null || privateBase64.isBlank()
                || publicBase64 == null || publicBase64.isBlank()) {
            throw new IllegalStateException("ORDER_DELIVERY_INTERNAL key material is required");
        }
        byte[] privateBytes = null;
        byte[] publicBytes = null;
        try {
            privateBytes = Base64.getDecoder().decode(privateBase64);
            publicBytes = Base64.getDecoder().decode(publicBase64);
            KeyFactory factory = KeyFactory.getInstance("EC");
            var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            var publicKey = factory.generatePublic(new X509EncodedKeySpec(publicBytes));
            if (privateKey instanceof ECPrivateKey ecPrivate
                    && publicKey instanceof ECPublicKey ecPublic
                    && ecPrivate.getParams().getCurve().getField().getFieldSize() == 256
                    && ecPublic.getParams().getCurve().getField().getFieldSize() == 256) {
                return new InternalDeliveryKeyPair(ecPublic, ecPrivate);
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("ORDER_DELIVERY_INTERNAL keys must be valid P-256 keys");
        } finally {
            if (privateBytes != null) Arrays.fill(privateBytes, (byte) 0);
            if (publicBytes != null) Arrays.fill(publicBytes, (byte) 0);
        }
        throw new IllegalStateException("ORDER_DELIVERY_INTERNAL keys must be valid P-256 keys");
    }
}
