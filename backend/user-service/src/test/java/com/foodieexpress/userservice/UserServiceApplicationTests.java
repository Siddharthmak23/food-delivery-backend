package com.foodieexpress.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.foodieexpress.userservice.model.User;
import com.foodieexpress.userservice.model.UserRole;
import com.foodieexpress.userservice.security.JwtService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://127.0.0.1:27018/?serverSelectionTimeoutMS=100")
@AutoConfigureMockMvc
class UserServiceApplicationTests {

    private static final String JWT_SECRET = generateTestSecret();
    private static final KeyPair ORDER_INTERNAL_KEY_PAIR = generateInternalKeyPair();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> JWT_SECRET);
        registry.add("ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_INTERNAL_KEY_PAIR.getPublic().getEncoded()));
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
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private MongoConverter mongoConverter;

    @Test
    void contextStartsWithServiceConfiguration() {
        assertEquals("8081", port);
        assertEquals("food_user", database);
    }

    @Test
    void healthEndpointReturnsLivenessWithoutClaimingDatabaseHealth() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"service\":\"user-service\""));
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
    void registrationRejectsClientSuppliedRoles() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "person@example.com",
                                  "password": "long-test-password",
                                  "displayName": "Person",
                                  "roles": ["ADMIN"]
                                }
                                """))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":\"MALFORMED_REQUEST\""));
    }

    @Test
    void issuedJwtHasOnlyExpectedIdentityClaimsAndIsVerifiable() throws Exception {
        User user = new User("person@example.com", "not-returned", "Person", Instant.now());
        user.setId("671000000000000000000001");
        user.setRoles(Set.of(
                UserRole.CUSTOMER,
                UserRole.RESTAURANT_OWNER,
                UserRole.DELIVERY_OPERATOR,
                UserRole.ADMIN));

        JwtService.IssuedToken issued = jwtService.issue(user);
        Jwt jwt = jwtDecoder.decode(issued.value());

        assertEquals(user.getId(), jwt.getSubject());
        assertEquals(
                Set.of("CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_OPERATOR", "ADMIN"),
                Set.copyOf(jwt.getClaimAsStringList("roles")));
        assertEquals(issued.expiresAt(), jwt.getExpiresAt());
        assertFalse(jwt.getClaims().containsKey("email"));
        assertFalse(jwt.getClaims().containsKey("passwordHash"));
        assertEquals("HS256", JWSObject.parse(issued.value()).getHeader().getAlgorithm().getName());
    }

    @Test
    void mongoRoleValuesForAllDocumentedRolesDeserialize() {
        User user = mongoConverter.read(User.class, new org.bson.Document(Map.of(
                "roles", List.of("CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_OPERATOR", "ADMIN"))));

        assertEquals(
                Set.of(UserRole.CUSTOMER, UserRole.RESTAURANT_OWNER, UserRole.DELIVERY_OPERATOR, UserRole.ADMIN),
                user.getRoles());
    }

    @Test
    void jwtDecoderRejectsTamperingExpiryMissingExpiryAndUnexpectedAlgorithm() throws Exception {
        String validToken = jwtService.issue(testUser()).value();
        String[] tokenParts = validToken.split("\\.");
        String tampered = tokenParts[0] + "." + tokenParts[1] + ".AAAA";
        assertThrows(JwtException.class, () -> jwtDecoder.decode(tampered));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(
                encodeHsToken(Instant.now().minusSeconds(600), Instant.now().minusSeconds(300), true)));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(
                encodeHsToken(Instant.now().minusSeconds(5), null, true)));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(
                encodeHsToken(Instant.now().minusSeconds(5), Instant.now().plusSeconds(300), false)));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(createRs256Token()));
    }

    @Test
    void validJwtAuthenticatesProtectedPathAndMalformedBearerIsRejected() throws Exception {
        String validToken = jwtService.issue(testUser()).value();
        MvcResult authorized = mockMvc.perform(get("/api/future")
                        .header("Authorization", "Bearer " + validToken))
                .andReturn();
        assertEquals(404, authorized.getResponse().getStatus());

        MvcResult malformed = mockMvc.perform(get("/api/future")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andReturn();
        assertEquals(401, malformed.getResponse().getStatus());
    }

    @Test
    void internalAddressLookupRequiresOrderServiceTokenAndNarrowScope() throws Exception {
        String path = "/internal/users/671000000000000000000001/addresses/671000000000000000000010";
        assertEquals(401, mockMvc.perform(get(path)).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + jwtService.issue(testUser()).value()))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + internalAddressToken("menu:read")))
                .andReturn().getResponse().getStatus());

        String[] parts = internalAddressToken("user:address:read").split("\\.");
        assertEquals(401, mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + parts[0] + "." + parts[1] + ".AAAA"))
                .andReturn().getResponse().getStatus());

        MvcResult valid = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + internalAddressToken("user:address:read")))
                .andReturn();
        assertEquals(503, valid.getResponse().getStatus());
        assertTrue(valid.getResponse().getContentAsString().contains("\"code\":\"DEPENDENCY_UNAVAILABLE\""));
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndRejectsWildcard() {
        var cors = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());

        assertEquals("http://localhost:3000", cors.checkOrigin("http://localhost:3000"));
        assertNull(cors.checkOrigin("https://unconfigured.example"));
        assertThrows(IllegalStateException.class,
                () -> new com.foodieexpress.userservice.config.SecurityConfiguration()
                        .corsConfigurationSource("*"));
    }

    private User testUser() {
        User user = new User("person@example.com", "not-returned", "Person", Instant.now());
        user.setId("671000000000000000000001");
        user.setRoles(Set.of(UserRole.CUSTOMER));
        return user;
    }

    private String encodeHsToken(Instant issuedAt, Instant expiresAt, boolean includeRoles) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-user-service")
                .audience(List.of("foodieexpress-api"))
                .subject("671000000000000000000001")
                .issuedAt(issuedAt);
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        if (includeRoles) {
            claims.claim("roles", List.of("CUSTOMER"));
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private String createRs256Token() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Instant now = Instant.now();
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                new JWTClaimsSet.Builder()
                        .issuer("foodieexpress-user-service")
                        .audience("foodieexpress-api")
                        .subject("671000000000000000000001")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .claim("roles", List.of("CUSTOMER"))
                        .build());
        signedJwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return signedJwt.serialize();
    }

    private static String generateTestSecret() {
        try {
            byte[] secret = new byte[32];
            SecureRandom.getInstanceStrong().nextBytes(secret);
            return Base64.getEncoder().encodeToString(secret);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to generate test JWT secret", exception);
        }
    }

    private String internalAddressToken(String scope) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-order-service")
                .subject("order-service")
                .audience(List.of("foodieexpress-user-service"))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("scopes", List.of(scope))
                .build();
        var encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withKeyPair(
                (java.security.interfaces.ECPublicKey) ORDER_INTERNAL_KEY_PAIR.getPublic(),
                (java.security.interfaces.ECPrivateKey) ORDER_INTERNAL_KEY_PAIR.getPrivate())
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
            throw new IllegalStateException("Unable to generate internal test key pair", exception);
        }
    }
}
