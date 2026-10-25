package com.foodieexpress.deliveryservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.foodieexpress.deliveryservice.security.InternalOrderTokenService;
import com.nimbusds.jose.JWSObject;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://127.0.0.1:27018/?serverSelectionTimeoutMS=100")
@AutoConfigureMockMvc
class DeliveryServiceApplicationTests {

    private static final String JWT_SECRET = generateTestSecret();
    private static final KeyPair ORDER_DELIVERY_KEYS = generateInternalKeyPair();
    private static final KeyPair DELIVERY_ORDER_KEYS = generateInternalKeyPair();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEYS.getPublic().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEYS.getPrivate().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEYS.getPublic().getEncoded()));
    }

    @Value("${server.port}")
    private String port;

    @Value("${spring.mongodb.database}")
    private String database;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    @Qualifier("jwtEncoder")
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private InternalOrderTokenService internalOrderTokenService;

    @Test
    void contextStartsWithServiceConfiguration() {
        assertEquals("8084", port);
        assertEquals("food_delivery", database);
    }

    @Test
    void healthEndpointReturnsLivenessWithoutClaimingDatabaseHealth() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"service\":\"delivery-service\""));
        assertTrue(result.getResponse().getContentAsString().contains("\"status\":\"UP\""));
        assertTrue(result.getResponse().getContentAsString().contains("\"databaseStatus\":\"NOT_CHECKED\""));
    }

    @Test
    void futureEndpointsRequireAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/future")).andReturn();

        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"UNAUTHENTICATED\""));
        assertFalse(result.getResponse().getContentAsString().contains("\"fieldErrors\""));
    }

    @Test
    void provisioningRequiresDedicatedOrderServiceTokenAndRejectsUserCredentials() throws Exception {
        assertEquals(401, mockMvc.perform(post("/api/deliveries")).andReturn().getResponse().getStatus());

        assertEquals(403, mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + userToken("CUSTOMER"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus());

        String orderToken = orderToken("delivery:create", Instant.now().plusSeconds(60));
        assertEquals(400, mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + orderToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus());

        String[] parts = orderToken.split("\\.");
        MvcResult tamperedInternalToken = mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + parts[0] + "." + parts[1] + ".AAAA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertEquals(401, tamperedInternalToken.getResponse().getStatus());
        assertTrue(tamperedInternalToken.getResponse().getContentAsString().contains(
                "\"code\":\"UNAUTHENTICATED\""));
        assertEquals(401, mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + orderToken(
                                "delivery:create", Instant.now().minusSeconds(300)))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + orderToken(
                                "delivery:cancel", Instant.now().plusSeconds(60)))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void provisioningRejectsNonInrCurrencyBeforePersistence() throws Exception {
        String orderToken = orderToken("delivery:create", Instant.now().plusSeconds(60));
        String orderId = "673000000000000000000001";
        MvcResult result = mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + orderToken)
                        .header("Idempotency-Key", orderId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId":"673000000000000000000001",
                                  "restaurantId":"672000000000000000000001",
                                  "customerId":"671000000000000000000001",
                                  "pickupAddress":{"recipientName":"Kitchen","line1":"1 Main St","city":"City",
                                    "region":"State","postalCode":"380001","countryCode":"IN","phone":"+919876543210"},
                                  "deliveryAddress":{"recipientName":"Customer","line1":"2 Main St","city":"City",
                                    "region":"State","postalCode":"380001","countryCode":"IN","phone":"+919876543210"},
                                  "currency":"USD"
                                }
                                """))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
    }

    @Test
    void deliveryCallbackTokensUseIndependentKeyAndAudience() throws Exception {
        var parsed = JWSObject.parse(internalOrderTokenService.issueStatusUpdateToken());
        assertEquals("ES256", parsed.getHeader().getAlgorithm().getName());
        assertEquals("foodieexpress-delivery-service", parsed.getPayload().toJSONObject().get("iss"));
        assertEquals("delivery-service", parsed.getPayload().toJSONObject().get("sub"));
        assertEquals("foodieexpress-order-service", parsed.getPayload().toJSONObject().get("aud"));
        assertEquals(List.of("order:delivery-status:write"),
                parsed.getPayload().toJSONObject().get("scopes"));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(
                internalOrderTokenService.issueStatusUpdateToken()));
    }

    @Test
    void hs256TokenAuthenticatesAndInvalidOrExpiredTokensAreRejected() throws Exception {
        String token = tokenWithExpiry(Instant.now().plusSeconds(300));
        MvcResult valid = mockMvc.perform(get("/api/future")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertEquals(403, valid.getResponse().getStatus());

        String[] parts = token.split("\\.");
        MvcResult tampered = mockMvc.perform(get("/api/future")
                        .header("Authorization", "Bearer " + parts[0] + "." + parts[1] + ".AAAA"))
                .andReturn();
        assertEquals(401, tampered.getResponse().getStatus());
        assertThrows(JwtException.class,
                () -> jwtDecoder.decode(tokenWithExpiry(Instant.now().minusSeconds(300))));
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndRejectsWildcard() {
        var cors = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());

        assertEquals("http://localhost:3000", cors.checkOrigin("http://localhost:3000"));
        assertNull(cors.checkOrigin("https://unconfigured.example"));
        assertThrows(IllegalStateException.class,
                () -> new com.foodieexpress.deliveryservice.config.SecurityConfiguration()
                        .corsConfigurationSource("*"));
    }

    private String tokenWithExpiry(Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(60)
                : Instant.now().minusSeconds(1);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-user-service")
                .audience(List.of("foodieexpress-api"))
                .subject("671000000000000000000001")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("CUSTOMER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String userToken(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-user-service")
                .audience(List.of("foodieexpress-api"))
                .subject("671000000000000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String orderToken(String scope, Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(60)
                : Instant.now().minusSeconds(1);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-order-service")
                .audience(List.of("foodieexpress-delivery-service"))
                .subject("order-service")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scopes", List.of(scope))
                .build();
        JwtEncoder encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withKeyPair(
                (java.security.interfaces.ECPublicKey) ORDER_DELIVERY_KEYS.getPublic(),
                (java.security.interfaces.ECPrivateKey) ORDER_DELIVERY_KEYS.getPrivate()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.ES256).build(), claims)).getTokenValue();
    }

    private static String generateTestSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    private static KeyPair generateInternalKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not generate test internal key pair", exception);
        }
    }
}
