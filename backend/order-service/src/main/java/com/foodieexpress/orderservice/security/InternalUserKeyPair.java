package com.foodieexpress.orderservice.security;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public record InternalUserKeyPair(ECPublicKey publicKey, ECPrivateKey privateKey) {

    public static InternalUserKeyPair parse(String privateKeyBase64, String publicKeyBase64) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()
                || publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw invalidKeys();
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            var privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
            var publicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            if (!(privateKey instanceof ECPrivateKey ecPrivateKey)
                    || !(publicKey instanceof ECPublicKey ecPublicKey)
                    || ecPrivateKey.getParams().getCurve().getField().getFieldSize() != 256
                    || ecPublicKey.getParams().getCurve().getField().getFieldSize() != 256
                    || ecPrivateKey.getParams().getOrder().bitLength() != 256
                    || ecPublicKey.getParams().getOrder().bitLength() != 256) {
                throw invalidKeys();
            }
            verifyPair(ecPrivateKey, ecPublicKey);
            return new InternalUserKeyPair(ecPublicKey, ecPrivateKey);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw invalidKeys();
        }
    }

    private static void verifyPair(ECPrivateKey privateKey, ECPublicKey publicKey)
            throws java.security.GeneralSecurityException {
        byte[] challenge = "foodieexpress-order-to-user-key-pair-check"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        boolean matches = verifier.verify(signature);
        java.util.Arrays.fill(signature, (byte) 0);
        if (!matches) {
            throw invalidKeys();
        }
    }

    private static IllegalStateException invalidKeys() {
        return new IllegalStateException(
                "Order-to-User internal keys must be a matching P-256 key pair in Base64 DER format");
    }
}
