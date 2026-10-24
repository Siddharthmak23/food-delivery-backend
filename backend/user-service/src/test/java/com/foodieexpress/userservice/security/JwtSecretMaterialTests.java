package com.foodieexpress.userservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtSecretMaterialTests {

    @Test
    void missingMalformedAndShortSecretsFailWithoutEchoingSecret() {
        assertThrows(IllegalStateException.class, () -> JwtSecretMaterial.parse(""));
        assertThrows(IllegalStateException.class, () -> JwtSecretMaterial.parse("not-base64"));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretMaterial.parse(Base64.getEncoder().encodeToString(new byte[31])));
    }

    @Test
    void acceptsAtLeast256BitsOfBase64EncodedKeyMaterial() {
        var secret = JwtSecretMaterial.parse(Base64.getEncoder().encodeToString(new byte[32]));

        assertEquals("HmacSHA256", secret.getAlgorithm());
        assertEquals(32, secret.getEncoded().length);
    }
}
