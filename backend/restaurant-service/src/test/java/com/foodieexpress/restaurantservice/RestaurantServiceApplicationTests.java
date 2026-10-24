package com.foodieexpress.restaurantservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.SecureRandom;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
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

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://127.0.0.1:27018/?serverSelectionTimeoutMS=100")
@AutoConfigureMockMvc
class RestaurantServiceApplicationTests {

    private static final String JWT_SECRET = generateTestSecret();
    private static final KeyPair INTERNAL_KEY_PAIR = generateInternalKeyPair();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(INTERNAL_KEY_PAIR.getPublic().getEncoded()));
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
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void contextStartsWithServiceConfiguration() {
        assertEquals("8082", port);
        assertEquals("food_restaurant", database);
    }

    @Test
    void healthEndpointReturnsLivenessWithoutClaimingDatabaseHealth() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"service\":\"restaurant-service\""));
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
    void restaurantOwnerWritesRequireOwnerRole() throws Exception {
        String customerToken = tokenWithExpiry(Instant.now().plusSeconds(300));
        MvcResult result = mockMvc.perform(post("/api/restaurants")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertEquals(403, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"FORBIDDEN\""));
    }

    @Test
    void internalRoutesRejectMissingUserAndTamperedTokens() throws Exception {
        String path = "/internal/restaurants/672000000000000000000001/owners/671000000000000000000001";
        assertEquals(401, mockMvc.perform(get(path)).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get(path)
                        .header("Authorization", "Bearer "
                                + tokenWithExpiry(Instant.now().plusSeconds(300))))
                .andReturn().getResponse().getStatus());

        String[] segments = internalToken("restaurant:ownership:read").split("\\.");
        assertEquals(401, mockMvc.perform(get(path)
                        .header("Authorization",
                                "Bearer " + segments[0] + "." + segments[1] + ".AAAA"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void internalRouteRejectsValidTokenWithoutRequiredScope() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/internal/restaurants/672000000000000000000001/owners/671000000000000000000001")
                                .header("Authorization", "Bearer " + internalToken("menu:read")))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());

        MvcResult deliveryDetails = mockMvc.perform(
                        get("/internal/restaurants/672000000000000000000001/delivery-details")
                                .header("Authorization", "Bearer " + internalToken("restaurant:ownership:read")))
                .andReturn();
        assertEquals(403, deliveryDetails.getResponse().getStatus());
    }

    @Test
    void databaseFailuresAreReportedAsServiceUnavailable() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/restaurants")).andReturn();

        assertEquals(503, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"DEPENDENCY_UNAVAILABLE\""));
        assertFalse(result.getResponse().getContentAsString().contains("MongoSocket"));
    }

    @Test
    void paginationSortAndObjectIdInputsAreValidated() throws Exception {
        MvcResult excessivePageSize = mockMvc.perform(get("/api/restaurants").param("size", "101")).andReturn();
        assertEquals(400, excessivePageSize.getResponse().getStatus());

        MvcResult unsupportedSort = mockMvc.perform(get("/api/restaurants").param("sort", "price,asc")).andReturn();
        assertEquals(400, unsupportedSort.getResponse().getStatus());

        MvcResult malformedId = mockMvc.perform(get("/api/restaurants/not-an-object-id")).andReturn();
        assertEquals(400, malformedId.getResponse().getStatus());
    }

    @Test
    void hs256TokenAuthenticatesAndInvalidOrExpiredTokensAreRejected() throws Exception {
        String token = tokenWithExpiry(Instant.now().plusSeconds(300));
        MvcResult valid = mockMvc.perform(get("/api/future")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertEquals(404, valid.getResponse().getStatus());

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
                () -> new com.foodieexpress.restaurantservice.config.SecurityConfiguration()
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

    private String internalToken(String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-order-service")
                .subject("order-service")
                .audience(List.of("foodieexpress-restaurant-service"))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("scopes", List.of(scope))
                .build();
        var encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withKeyPair(
                (java.security.interfaces.ECPublicKey) INTERNAL_KEY_PAIR.getPublic(),
                (java.security.interfaces.ECPrivateKey) INTERNAL_KEY_PAIR.getPrivate())
                .build();
        return encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256).build(), claims))
                .getTokenValue();
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

    private static String generateTestSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
