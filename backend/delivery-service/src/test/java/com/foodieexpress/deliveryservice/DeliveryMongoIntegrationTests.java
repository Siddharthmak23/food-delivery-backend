package com.foodieexpress.deliveryservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodieexpress.deliveryservice.model.Delivery;
import com.foodieexpress.deliveryservice.repository.DeliveryRepository;
import com.foodieexpress.deliveryservice.service.OrderStatusClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=500")
@AutoConfigureMockMvc
@Import(DeliveryMongoIntegrationTests.TestClientsConfiguration.class)
@EnabledIfSystemProperty(named = "delivery.mongo.integration", matches = "true")
class DeliveryMongoIntegrationTests {

    private static final String CUSTOMER_ID = "671000000000000000000001";
    private static final String OTHER_CUSTOMER_ID = "671000000000000000000002";
    private static final String RESTAURANT_ID = "672000000000000000000001";
    private static final String ORDER_ID = "673000000000000000000001";
    private static final String ORDER_SECRET = testSecret();
    private static final String TEST_DATABASE = System.getProperty(
            "delivery.mongo.integration.database", "food_delivery_phase6_test");
    private static final KeyPair ORDER_DELIVERY_KEYS = generateKeyPair();
    private static final KeyPair DELIVERY_ORDER_KEYS = generateKeyPair();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> ORDER_SECRET);
        registry.add("MONGODB_DATABASE", () -> TEST_DATABASE);
        registry.add("ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEYS.getPublic().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEYS.getPrivate().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEYS.getPublic().getEncoded()));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StubOrderStatusClient orderStatusClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("jwtEncoder")
    private JwtEncoder userJwtEncoder;

    @BeforeEach
    void prepareDatabase() {
        mongoTemplate.indexOps(Delivery.class).ensureIndex(
                new Index().on("orderId", Sort.Direction.ASC).unique().named("delivery_order_unique"));
        mongoTemplate.indexOps(Delivery.class).ensureIndex(
                new Index().on("customerId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC).named("delivery_customer_created_idx"));
        mongoTemplate.indexOps(Delivery.class).ensureIndex(
                new Index().on("restaurantId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC).named("delivery_restaurant_created_idx"));
        mongoTemplate.indexOps(Delivery.class).ensureIndex(
                new Index().on("status", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC).named("delivery_status_created_idx"));
        repository.deleteAll();
        orderStatusClient.reset();
    }

    @Test
    void internalProvisioningIsUniqueAndConflictingReplayIsRejected() throws Exception {
        MvcResult result = createDelivery(ORDER_ID, "initial-request")
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(ORDER_ID, response.get("orderId").asText());
        assertEquals("PENDING_ASSIGNMENT", response.get("status").asText());
        assertEquals(1, response.get("events").size());

        createDelivery(ORDER_ID, "initial-request").andExpect(status().isOk());
        assertEquals(1, repository.count());
        createDelivery(ORDER_ID, "different-request")
                .andExpect(status().isConflict());
        assertEquals(1, repository.count());
        var index = mongoTemplate.indexOps(Delivery.class).getIndexInfo().stream()
                .filter(info -> info.getName().equals("delivery_order_unique"))
                .findFirst().orElseThrow();
        assertTrue(index.isUnique());
    }

    @Test
    void assignmentAndLifecycleEnforceTransitionsAndConfiguredPartners() throws Exception {
        String deliveryId = createAndGetId(ORDER_ID);
        patchAsDelivery(deliveryId, "/assign", """
                {"partnerId":"not-configured"}
                """).andExpect(status().isUnprocessableEntity());
        patchAsDelivery(deliveryId, "/assign", """
                {"partnerId":"demo-partner-01"}
                """).andExpect(status().isOk());
        patchAsDelivery(deliveryId, "/assign", """
                {"partnerId":"demo-partner-01"}
                """).andExpect(status().isOk());
        patchAsDelivery(deliveryId, "/status", """
                {"status":"PICKED_UP"}
                """).andExpect(status().isOk());
        patchAsDelivery(deliveryId, "/status", """
                {"status":"DELIVERED"}
                """).andExpect(status().isOk());
        patchAsDelivery(deliveryId, "/status", """
                {"status":"PICKED_UP"}
                """).andExpect(status().isConflict());
        assertEquals("DELIVERED", repository.findById(deliveryId).orElseThrow().getStatus().name());
        assertEquals(2, orderStatusClient.calls());
    }

    @Test
    void failedOrderCallbackLeavesDurableEventAndSameStatusRetriesIdempotently() throws Exception {
        String deliveryId = createAndGetId(ORDER_ID);
        patchAsDelivery(deliveryId, "/assign", """
                {"partnerId":"demo-partner-01"}
                """).andExpect(status().isOk());

        orderStatusClient.failNext.set(true);
        patchAsDelivery(deliveryId, "/status", """
                {"status":"PICKED_UP"}
                """).andExpect(status().isServiceUnavailable());
        Delivery persisted = repository.findById(deliveryId).orElseThrow();
        assertEquals("PICKED_UP", persisted.getStatus().name());
        assertTrue(persisted.isOrderCallbackPending());
        String eventId = persisted.getEvents().getLast().eventId();

        patchAsDelivery(deliveryId, "/status", """
                {"status":"PICKED_UP"}
                """).andExpect(status().isOk());
        Delivery retried = repository.findById(deliveryId).orElseThrow();
        assertFalse(retried.isOrderCallbackPending());
        assertFalse(retried.getEvents().getLast().callbackPending());
        assertEquals(eventId, retried.getEvents().getLast().eventId());
        assertEquals(2, orderStatusClient.calls());
    }

    @Test
    void customersReadOnlyTheirOwnDeliveryAndCannotMutateLifecycle() throws Exception {
        String deliveryId = createAndGetId(ORDER_ID);
        mockMvc.perform(get("/api/deliveries/{id}", deliveryId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/deliveries/{id}", deliveryId)
                        .header("Authorization", "Bearer " + userToken(OTHER_CUSTOMER_ID, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/deliveries/{id}/assign", deliveryId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerId\":\"demo-partner-01\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/deliveries/order/{orderId}", ORDER_ID)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void cancellationIsAllowedOnlyBeforePickupAndNotifiesOrderService() throws Exception {
        String deliveryId = createAndGetId(ORDER_ID);
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", deliveryId)
                .header("Authorization", "Bearer " + orderToken("delivery:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"reason":"Order cancelled"}
                """)).andExpect(status().isOk());
        assertEquals("CANCELLED", repository.findById(deliveryId).orElseThrow().getStatus().name());
        assertEquals(1, orderStatusClient.calls());
        patchAsDelivery(deliveryId, "/status", """
                {"status":"PICKED_UP"}
                """).andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions createDelivery(String orderId, String line1)
            throws Exception {
        return mockMvc.perform(post("/api/deliveries")
                .header("Authorization", "Bearer " + orderToken())
                .header("Idempotency-Key", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(orderId, line1)));
    }

    private String createAndGetId(String orderId) throws Exception {
        var result = createDelivery(orderId, "10 Main St")
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions patchAsDelivery(
            String deliveryId, String suffix, String body) throws Exception {
        return mockMvc.perform(patch("/api/deliveries/{id}" + suffix, deliveryId)
                .header("Authorization", "Bearer " + userToken(OTHER_CUSTOMER_ID, "DELIVERY_OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createBody(String orderId, String addressLine) {
        return """
                {
                  "orderId":"%s",
                  "restaurantId":"%s",
                  "customerId":"%s",
                  "pickupAddress":{"recipientName":"Kitchen","line1":"20 Market St","city":"City",
                    "region":"CA","postalCode":"90000","countryCode":"US","phone":"+10000000000"},
                  "deliveryAddress":{"recipientName":"Customer","line1":"%s","city":"City",
                    "region":"CA","postalCode":"90000","countryCode":"US","phone":"+10000000000"},
                  "currency":"USD"
                }
                """.formatted(orderId, RESTAURANT_ID, CUSTOMER_ID, addressLine);
    }

    private String orderToken() {
        return orderToken("delivery:create");
    }

    private String orderToken(String scope) {
        var now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-order-service")
                .audience(List.of("foodieexpress-delivery-service"))
                .subject("order-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("scopes", List.of(scope))
                .build();
        JwtEncoder encoder = org.springframework.security.oauth2.jwt.NimbusJwtEncoder.withKeyPair(
                (java.security.interfaces.ECPublicKey) ORDER_DELIVERY_KEYS.getPublic(),
                (java.security.interfaces.ECPrivateKey) ORDER_DELIVERY_KEYS.getPrivate()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.ES256).build(), claims)).getTokenValue();
    }

    private String userToken(String userId, String role) {
        var now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-user-service")
                .audience(List.of("foodieexpress-api"))
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role))
                .build();
        return userJwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClientsConfiguration {
        @Bean
        @Primary
        StubOrderStatusClient orderStatusClient() {
            return new StubOrderStatusClient();
        }
    }

    static class StubOrderStatusClient implements OrderStatusClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();

        @Override
        public void report(String orderId, String deliveryId, com.foodieexpress.deliveryservice.model.DeliveryEvent event) {
            callCount.incrementAndGet();
            if (failNext.getAndSet(false)) {
                throw new com.foodieexpress.deliveryservice.exception.OrderCallbackException();
            }
        }

        int calls() { return callCount.get(); }

        void reset() {
            callCount.set(0);
            failNext.set(false);
        }
    }

    private static String testSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
