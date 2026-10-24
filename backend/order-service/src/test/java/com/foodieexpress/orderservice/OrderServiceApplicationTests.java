package com.foodieexpress.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.foodieexpress.orderservice.security.InternalRestaurantTokenService;
import com.foodieexpress.orderservice.security.InternalDeliveryTokenService;
import com.foodieexpress.orderservice.security.InternalUserTokenService;
import com.foodieexpress.orderservice.service.HttpAddressLookupClient;
import com.foodieexpress.orderservice.service.HttpRestaurantQuoteClient;
import com.foodieexpress.orderservice.service.HttpRestaurantOwnerClient;
import com.foodieexpress.orderservice.service.HttpDeliveryProvisioningClient;
import com.foodieexpress.orderservice.dto.DeliveryCreateRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteRequest;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.exception.ResourceNotFoundException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.nimbusds.jose.JWSObject;
import java.security.SecureRandom;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.io.IOException;
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
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
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
class OrderServiceApplicationTests {

    private static final String JWT_SECRET = generateTestSecret();
    private static final KeyPair INTERNAL_KEY_PAIR = generateInternalKeyPair();
    private static final KeyPair USER_INTERNAL_KEY_PAIR = generateInternalKeyPair();
    private static final KeyPair ORDER_DELIVERY_KEY_PAIR = generateInternalKeyPair();
    private static final KeyPair DELIVERY_ORDER_KEY_PAIR = generateInternalKeyPair();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(INTERNAL_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(INTERNAL_KEY_PAIR.getPublic().getEncoded()));
        registry.add("ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(USER_INTERNAL_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(USER_INTERNAL_KEY_PAIR.getPublic().getEncoded()));
        registry.add("ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEY_PAIR.getPrivate().getEncoded()));
        registry.add("ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEY_PAIR.getPublic().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEY_PAIR.getPublic().getEncoded()));
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
    private InternalRestaurantTokenService internalRestaurantTokenService;

    @Autowired
    private InternalUserTokenService internalUserTokenService;

    @Autowired
    private InternalDeliveryTokenService internalDeliveryTokenService;

    @Test
    void contextStartsWithServiceConfiguration() {
        assertEquals("8083", port);
        assertEquals("food_order", database);
    }

    @Test
    void healthEndpointReturnsLivenessWithoutClaimingDatabaseHealth() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/health")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"service\":\"order-service\""));
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
    void customerOrderRoutesRequireCustomerAuthenticationAndRejectPriceInput() throws Exception {
        assertEquals(401, mockMvc.perform(get("/api/orders/my-orders")).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(post("/api/orders")).andReturn().getResponse().getStatus());

        String body = """
                {"restaurantId":"672000000000000000000001",
                 "items":[{"menuItemId":"672000000000000000000010","quantity":1,"price":"0.01"}],
                 "addressId":"671000000000000000000010"}
                """;
        MvcResult clientPrice = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + tokenWithRole("CUSTOMER"))
                        .header("Idempotency-Key", "client-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertEquals(400, clientPrice.getResponse().getStatus());

        MvcResult wrongRole = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + tokenWithRole("DELIVERY_OPERATOR"))
                        .header("Idempotency-Key", "wrong-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restaurantId":"672000000000000000000001",
                                 "items":[{"menuItemId":"672000000000000000000010","quantity":1}],
                                 "addressId":"671000000000000000000010"}
                                """))
                .andReturn();
        assertEquals(403, wrongRole.getResponse().getStatus());
    }

    @Test
    void orderHistoryReportsMongoFailureAndBoundsPagination() throws Exception {
        MvcResult failed = mockMvc.perform(get("/api/orders/my-orders")
                        .header("Authorization", "Bearer " + tokenWithRole("CUSTOMER")))
                .andReturn();
        assertEquals(503, failed.getResponse().getStatus());
        assertTrue(failed.getResponse().getContentAsString().contains("\"code\":\"DEPENDENCY_UNAVAILABLE\""));

        MvcResult invalidPage = mockMvc.perform(get("/api/orders/my-orders")
                        .header("Authorization", "Bearer " + tokenWithRole("CUSTOMER"))
                        .param("size", "101"))
                .andReturn();
        assertEquals(400, invalidPage.getResponse().getStatus());
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
    void internalRestaurantTokensUseDedicatedKeyAndNarrowScopes() throws Exception {
        String menuToken = internalRestaurantTokenService.issueMenuReadToken();
        var parsed = JWSObject.parse(menuToken);
        assertEquals("ES256", parsed.getHeader().getAlgorithm().getName());
        var claims = parsed.getPayload().toJSONObject();
        assertEquals("foodieexpress-order-service", claims.get("iss"));
        assertEquals("order-service", claims.get("sub"));
        assertEquals("foodieexpress-restaurant-service", claims.get("aud"));
        assertEquals(List.of("menu:read"), claims.get("scopes"));
        assertThrows(JwtException.class, () -> jwtDecoder.decode(menuToken));
        assertEquals(
                List.of("restaurant:ownership:read"),
                JWSObject.parse(internalRestaurantTokenService.issueOwnerVerificationToken())
                        .getPayload().toJSONObject().get("scopes"));
    }

    @Test
    void internalUserAddressTokenUsesSeparateKeyAudienceAndScope() throws Exception {
        var parsed = JWSObject.parse(internalUserTokenService.issueAddressReadToken());
        assertEquals("ES256", parsed.getHeader().getAlgorithm().getName());
        var claims = parsed.getPayload().toJSONObject();
        assertEquals("foodieexpress-order-service", claims.get("iss"));
        assertEquals("order-service", claims.get("sub"));
        assertEquals("foodieexpress-user-service", claims.get("aud"));
        assertEquals(List.of("user:address:read"), claims.get("scopes"));
    }

    @Test
    void orderDeliveryTokensUseDedicatedKeyPairAndNarrowScope() throws Exception {
        var parsed = JWSObject.parse(internalDeliveryTokenService.issueCreateToken());
        assertEquals("ES256", parsed.getHeader().getAlgorithm().getName());
        var claims = parsed.getPayload().toJSONObject();
        assertEquals("foodieexpress-order-service", claims.get("iss"));
        assertEquals("order-service", claims.get("sub"));
        assertEquals("foodieexpress-delivery-service", claims.get("aud"));
        assertEquals(List.of("delivery:create"), claims.get("scopes"));
        assertEquals(List.of("delivery:cancel"),
                JWSObject.parse(internalDeliveryTokenService.issueCancelToken())
                        .getPayload().toJSONObject().get("scopes"));
        assertEquals(List.of("delivery:create"),
                JWSObject.parse(internalDeliveryTokenService.issueCreateToken())
                        .getPayload().toJSONObject().get("scopes"));
    }

    @Test
    void downstreamClientsSendScopedBearerTokensAndParseTheirContracts() {
        RestClient.Builder userBuilder = RestClient.builder().baseUrl("http://user-service");
        MockRestServiceServer userServer = MockRestServiceServer.bindTo(userBuilder).build();
        userServer.expect(requestTo(
                        "http://user-service/internal/users/671000000000000000000001/addresses/671000000000000000000010"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"userId":"671000000000000000000001","address":{
                          "id":"671000000000000000000010","recipientName":"Customer",
                          "line1":"10 Main St","line2":null,"city":"Example City",
                          "region":"Maharashtra","postalCode":"400001","countryCode":"IN","phone":"+919876543210"}}
                        """, MediaType.APPLICATION_JSON));
        var address = new HttpAddressLookupClient(userBuilder.build(), internalUserTokenService)
                .getOwnedAddress("671000000000000000000001", "671000000000000000000010");
        assertEquals("10 Main St", address.address().line1());
        userServer.verify();

        RestClient.Builder unsupportedAddressBuilder = RestClient.builder().baseUrl("http://user-service");
        MockRestServiceServer unsupportedAddressServer = MockRestServiceServer.bindTo(unsupportedAddressBuilder).build();
        unsupportedAddressServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/users/")))
                .andRespond(withSuccess("""
                        {"userId":"671000000000000000000001","address":{
                          "id":"671000000000000000000010","recipientName":"Customer",
                          "line1":"10 Main St","line2":null,"city":"Example City",
                          "region":"California","postalCode":"90210","countryCode":"US","phone":"+14155550123"}}
                        """, MediaType.APPLICATION_JSON));
        assertThrows(UpstreamResponseException.class, () -> new HttpAddressLookupClient(
                        unsupportedAddressBuilder.build(), internalUserTokenService)
                .getOwnedAddress("671000000000000000000001", "671000000000000000000010"));
        unsupportedAddressServer.verify();

        RestClient.Builder restaurantBuilder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer restaurantServer = MockRestServiceServer.bindTo(restaurantBuilder).build();
        restaurantServer.expect(requestTo(
                        "http://restaurant-service/internal/restaurants/672000000000000000000001/menu-quote"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"restaurantId":"672000000000000000000001","items":[{
                          "id":"672000000000000000000010","restaurantId":"672000000000000000000001",
                          "name":"Pasta","quantity":2,"price":"12.50","currency":"USD","available":true}]}
                        """, MediaType.APPLICATION_JSON));
        var quote = new HttpRestaurantQuoteClient(restaurantBuilder.build(), internalRestaurantTokenService)
                .quote("672000000000000000000001", new RestaurantQuoteRequest(List.of(
                        new RestaurantQuoteRequest.Item("672000000000000000000010", 2))));
        assertEquals("12.50", quote.items().getFirst().price());
        restaurantServer.verify();
    }

    @Test
    void downstreamClientsMapNotFoundAuthorizationServerAndTimeoutFailures() throws IOException {
        RestClient.Builder missingBuilder = RestClient.builder().baseUrl("http://user-service");
        MockRestServiceServer missingServer = MockRestServiceServer.bindTo(missingBuilder).build();
        missingServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/users/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(ResourceNotFoundException.class, () -> new HttpAddressLookupClient(
                        missingBuilder.build(), internalUserTokenService)
                .getOwnedAddress("671000000000000000000001", "671000000000000000000010"));
        missingServer.verify();

        RestClient.Builder unauthorizedBuilder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer unauthorizedServer = MockRestServiceServer.bindTo(unauthorizedBuilder).build();
        unauthorizedServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/restaurants/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(UpstreamResponseException.class, () -> new HttpRestaurantQuoteClient(
                        unauthorizedBuilder.build(), internalRestaurantTokenService)
                .quote("672000000000000000000001", new RestaurantQuoteRequest(List.of(
                        new RestaurantQuoteRequest.Item("672000000000000000000010", 1)))));
        unauthorizedServer.verify();

        RestClient.Builder unavailableBuilder = RestClient.builder().baseUrl("http://user-service");
        MockRestServiceServer unavailableServer = MockRestServiceServer.bindTo(unavailableBuilder).build();
        unavailableServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/users/")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(DependencyUnavailableException.class, () -> new HttpAddressLookupClient(
                        unavailableBuilder.build(), internalUserTokenService)
                .getOwnedAddress("671000000000000000000001", "671000000000000000000010"));
        unavailableServer.verify();

        RestClient.Builder timeoutBuilder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();
        timeoutServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/restaurants/")))
                .andRespond(withException(new IOException("simulated timeout")));
        assertThrows(DependencyUnavailableException.class, () -> new HttpRestaurantQuoteClient(
                        timeoutBuilder.build(), internalRestaurantTokenService)
                .quote("672000000000000000000001", new RestaurantQuoteRequest(List.of(
                        new RestaurantQuoteRequest.Item("672000000000000000000010", 1)))));
        timeoutServer.verify();
    }

    @Test
    void lifecycleClientsUseScopedCredentialsAndStableDeliveryIdempotencyKey() {
        String orderId = "673000000000000000000001";
        String restaurantId = "672000000000000000000001";
        String customerId = "671000000000000000000001";
        RestClient.Builder deliveryBuilder = RestClient.builder().baseUrl("http://delivery-service");
        MockRestServiceServer deliveryServer = MockRestServiceServer.bindTo(deliveryBuilder).build();
        deliveryServer.expect(requestTo("http://delivery-service/api/deliveries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", orderId))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"id":"674000000000000000000001","orderId":"%s",
                         "restaurantId":"%s","customerId":"%s","currency":"USD",
                         "status":"PENDING_ASSIGNMENT"}
                        """.formatted(orderId, restaurantId, customerId), MediaType.APPLICATION_JSON));
        var created = new HttpDeliveryProvisioningClient(
                deliveryBuilder.build(), internalDeliveryTokenService)
                .create(orderId, new DeliveryCreateRequest(
                        orderId, restaurantId, customerId,
                        new DeliveryCreateRequest.Address(
                                "Restaurant", "1 Main St", null, "City", "CA", "90000", "US", null),
                        new DeliveryCreateRequest.Address(
                                "Customer", "2 Main St", null, "Mumbai", "Maharashtra", "400001", "IN", "+919876543210"),
                        "USD"));
        assertEquals(orderId, created.orderId());
        deliveryServer.verify();

        RestClient.Builder restaurantBuilder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer restaurantServer = MockRestServiceServer.bindTo(restaurantBuilder).build();
        restaurantServer.expect(requestTo(
                        "http://restaurant-service/internal/restaurants/"
                                + restaurantId + "/owners/" + customerId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        restaurantServer.expect(requestTo(
                        "http://restaurant-service/internal/restaurants/" + restaurantId + "/delivery-details"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"id":"%s","name":"Restaurant","address":{
                          "line1":"1 Main St","line2":null,"city":"City",
                          "region":"CA","postalCode":"90000","countryCode":"US"}}
                        """.formatted(restaurantId), MediaType.APPLICATION_JSON));
        var restaurantClient = new HttpRestaurantOwnerClient(
                restaurantBuilder.build(), internalRestaurantTokenService);
        assertTrue(restaurantClient.isOwner(restaurantId, customerId));
        assertEquals("Restaurant", restaurantClient.getDeliveryDetails(restaurantId).name());
        restaurantServer.verify();
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndRejectsWildcard() {
        var cors = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());

        assertEquals("http://localhost:3000", cors.checkOrigin("http://localhost:3000"));
        assertNull(cors.checkOrigin("https://unconfigured.example"));
        assertThrows(IllegalStateException.class,
                () -> new com.foodieexpress.orderservice.config.SecurityConfiguration()
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

    private String tokenWithRole(String role) {
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
