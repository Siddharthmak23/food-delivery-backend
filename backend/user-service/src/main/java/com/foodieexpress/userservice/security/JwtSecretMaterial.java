package com.foodieexpress.userservice.security;

import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class JwtSecretMaterial {

    private JwtSecretMaterial() {
    }

    public static SecretKey parse(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be Base64-encoded random data of at least 32 bytes");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET must be Base64-encoded random data of at least 32 bytes");
        }
        try {
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET must be Base64-encoded random data of at least 32 bytes");
            }
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
