package com.foodieexpress.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodieexpress.orderservice.dto.RestaurantQuoteRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteResponse;
import com.foodieexpress.orderservice.dto.DeliveryCreateRequest;
import com.foodieexpress.orderservice.dto.DeliveryCreateResponse;
import com.foodieexpress.orderservice.dto.RestaurantDeliveryDetailsResponse;
import com.foodieexpress.orderservice.dto.UserAddressResponse;
import com.foodieexpress.orderservice.model.Order;
import com.foodieexpress.orderservice.model.OrderStatus;
import com.foodieexpress.orderservice.model.DeliveryProvisioningStatus;
import com.foodieexpress.orderservice.model.CancellationStatus;
import com.foodieexpress.orderservice.repository.OrderRepository;
import com.foodieexpress.orderservice.service.AddressLookupClient;
import com.foodieexpress.orderservice.service.RestaurantQuoteClient;
import com.foodieexpress.orderservice.service.RestaurantOwnerClient;
import com.foodieexpress.orderservice.service.DeliveryProvisioningClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=500")
@AutoConfigureMockMvc
@Import(OrderMongoIntegrationTests.TestClientsConfiguration.class)
@EnabledIfSystemProperty(named = "order.mongo.integration", matches = "true")
class OrderMongoIntegrationTests {

    private static final String CUSTOMER_ID = "671000000000000000000001";
    private static final String OTHER_CUSTOMER_ID = "671000000000000000000002";
    private static final String RESTAURANT_ID = "672000000000000000000001";
    private static final String ITEM_ONE_ID = "672000000000000000000010";
    private static final String ITEM_TWO_ID = "672000000000000000000011";
    private static final String ADDRESS_ID = "671000000000000000000010";
    private static final String JWT_SECRET = testSecret();
    private static final String TEST_DATABASE = System.getProperty(
            "order.mongo.integration.database", "food_order_phase5_test");
    private static final KeyPair RESTAURANT_KEYS = generateKeyPair();
    private static final KeyPair USER_KEYS = generateKeyPair();
    private static final KeyPair ORDER_DELIVERY_KEYS = generateKeyPair();
    private static final KeyPair DELIVERY_ORDER_KEYS = generateKeyPair();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("MONGODB_DATABASE", () -> TEST_DATABASE);
        registry.add("ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(RESTAURANT_KEYS.getPrivate().getEncoded()));
        registry.add("ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(RESTAURANT_KEYS.getPublic().getEncoded()));
        registry.add("ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(USER_KEYS.getPrivate().getEncoded()));
        registry.add("ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(USER_KEYS.getPublic().getEncoded()));
        registry.add("ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEYS.getPrivate().getEncoded()));
        registry.add("ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(ORDER_DELIVERY_KEYS.getPublic().getEncoded()));
        registry.add("DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(DELIVERY_ORDER_KEYS.getPublic().getEncoded()));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StubRestaurantQuoteClient quoteClient;

    @Autowired
    private StubRestaurantOwnerClient ownerClient;

    @Autowired
    private StubDeliveryProvisioningClient deliveryClient;

    @Autowired
    @Qualifier("jwtEncoder")
    private JwtEncoder jwtEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareDatabase() {
        mongoTemplate.indexOps(Order.class).ensureIndex(
                new Index().on("customerId", Sort.Direction.ASC)
                        .on("idempotency.key", Sort.Direction.ASC)
                        .unique()
                        .named("customer_idempotency_unique"));
        mongoTemplate.indexOps(Order.class).ensureIndex(
                new Index().on("restaurantId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                        .named("restaurant_history_idx"));
        mongoTemplate.indexOps(Order.class).ensureIndex(
                new Index().on("customerId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .named("customer_history_idx"));
        orderRepository.deleteAll();
        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.NORMAL);
        quoteClient.resetCalls();
        ownerClient.setAuthorized(true);
        deliveryClient.setFail(false);
        deliveryClient.reset();
    }

    @Test
    void createsAndPersistsAuthoritativeSnapshotsWithDecimalTotals() throws Exception {
        MvcResult result = createOrder(CUSTOMER_ID, "checkout-001", validRequest()).andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals("PLACED", response.get("status").asText());
        assertEquals("SIMULATED_UNPAID", response.get("paymentStatus").asText());
        assertEquals("32.05", response.get("subtotal").asText());
        assertEquals("0.00", response.get("deliveryFee").asText());
        assertEquals("0.00", response.get("discount").asText());
        assertEquals("32.05", response.get("total").asText());
        assertEquals("INR", response.get("currency").asText());
        assertEquals("Pasta", response.get("items").get(0).get("name").asText());
        assertEquals("12.50", response.get("items").get(0).get("unitPrice").asText());
        assertEquals("10 Main St", response.get("deliveryAddress").get("line1").asText());

        Order stored = orderRepository.findAll().getFirst();
        assertEquals(CUSTOMER_ID, stored.getCustomerId());
        assertEquals("INR", stored.getCurrency());
        assertEquals("32.05", stored.getTotal().toPlainString());
        assertEquals(ADDRESS_ID, stored.getDeliveryAddress().addressId());
        assertEquals(1, quoteClient.calls());
    }

    @Test
    void idempotencyReturnsOriginalAndRejectsChangedPayload() throws Exception {
        createOrder(CUSTOMER_ID, "checkout-retry", validRequest()).andExpect(status().isCreated());
        createOrder(CUSTOMER_ID, "checkout-retry", validRequest()).andExpect(status().isOk());
        assertEquals(1, orderRepository.count());
        assertEquals(1, quoteClient.calls());

        createOrder(CUSTOMER_ID, "checkout-retry", changedAddressRequest())
                .andExpect(status().isConflict());
        assertEquals(1, orderRepository.count());
        assertEquals(1, quoteClient.calls());
    }

    @Test
    void rejectsUnavailableMissingMalformedAndNonInrQuotesWithoutSaving() throws Exception {
        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.UNAVAILABLE);
        createOrder(CUSTOMER_ID, "unavailable-item", validRequest()).andExpect(status().isUnprocessableEntity());
        assertEquals(0, orderRepository.count());

        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.MISSING_ITEM);
        createOrder(CUSTOMER_ID, "missing-item", validRequest()).andExpect(status().isNotFound());
        assertEquals(0, orderRepository.count());

        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.WRONG_RESTAURANT);
        createOrder(CUSTOMER_ID, "wrong-restaurant", validRequest()).andExpect(status().isBadGateway());
        assertEquals(0, orderRepository.count());

        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.UNSUPPORTED_CURRENCY);
        createOrder(CUSTOMER_ID, "wrong-currency", validRequest()).andExpect(status().isUnprocessableEntity());
        assertEquals(0, orderRepository.count());

        quoteClient.setScenario(StubRestaurantQuoteClient.Scenario.DEPENDENCY_FAILURE);
        createOrder(CUSTOMER_ID, "downstream-failure", validRequest()).andExpect(status().isServiceUnavailable());
        assertEquals(0, orderRepository.count());
    }

    @Test
    void rejectsClientPricesAndInvalidQuantitiesBeforePersistence() throws Exception {
        String withClientPrice = """
                {"restaurantId":"%s","items":[{"menuItemId":"%s","quantity":1,"price":"0.01"}],"addressId":"%s"}
                """.formatted(RESTAURANT_ID, ITEM_ONE_ID, ADDRESS_ID);
        createOrder(CUSTOMER_ID, "client-price", withClientPrice).andExpect(status().isBadRequest());

        String invalidQuantity = """
                {"restaurantId":"%s","items":[{"menuItemId":"%s","quantity":101}],"addressId":"%s"}
                """.formatted(RESTAURANT_ID, ITEM_ONE_ID, ADDRESS_ID);
        createOrder(CUSTOMER_ID, "bad-quantity", invalidQuantity).andExpect(status().isBadRequest());
        assertEquals(0, orderRepository.count());
        assertEquals(0, quoteClient.calls());
    }

    @Test
    void customerCanReadAndCancelOnlyTheirOwnPlacedOrders() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "customer-order", validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken(OTHER_CUSTOMER_ID)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/my-orders")
                        .header("Authorization", "Bearer " + userToken(OTHER_CUSTOMER_ID)))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("\"totalItems\":0")));

        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().contains("\"status\":\"CANCELLED\"")));
        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken(OTHER_CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        assertEquals("CANCELLED", orderRepository.findById(orderId).orElseThrow().getStatus().name());
    }

    @Test
    void customerCannotCancelAnOrderAfterItsStatusLeavesPlaced() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "confirmed-order", validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(com.foodieexpress.orderservice.model.OrderStatus.CONFIRMED);
        orderRepository.save(order);

        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
        assertEquals("CONFIRMED", orderRepository.findById(orderId).orElseThrow().getStatus().name());
    }

    @Test
    void restaurantOwnerMustOwnRestaurantAndAcceptanceProvisionsDeliveryBeforeReady() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "owner-acceptance", validRequest())
                .andExpect(status().isCreated()).andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String ownerToken = ownerToken(CUSTOMER_ID);

        mockMvc.perform(get("/api/orders/restaurant/{restaurantId}", RESTAURANT_ID)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().contains("\"totalItems\":1")));

        ownerClient.setAuthorized(false);
        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        ownerClient.setAuthorized(true);

        MvcResult accepted = mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String deliveryId = objectMapper.readTree(accepted.getResponse().getContentAsString())
                .get("deliveryId").asText();
        assertEquals("674000000000000000000001", deliveryId);
        assertEquals(DeliveryProvisioningStatus.COMPLETE,
                orderRepository.findById(orderId).orElseThrow().getDeliveryProvisioningStatus());
        assertEquals(orderId, deliveryClient.lastRequest().orderId());
        assertEquals("Restaurant", deliveryClient.lastRequest().pickupAddress().recipientName());

        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_FOR_PICKUP\"}"))
                .andExpect(status().isOk());
        assertEquals(OrderStatus.READY_FOR_PICKUP, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void provisioningTimeoutIsRetryableAndReusesTheOrderId() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "owner-retry", validRequest())
                .andExpect(status().isCreated()).andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        deliveryClient.setFail(true);

        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().contains("\"deliveryProvisioningStatus\":\"RETRY_REQUIRED\"")));
        assertNull(orderRepository.findById(orderId).orElseThrow().getDeliveryId());

        deliveryClient.setFail(false);
        mockMvc.perform(post("/api/orders/{id}/delivery-provisioning/retry", orderId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders/{id}/delivery-provisioning/retry", orderId)
                        .header("Authorization", "Bearer " + userTokenWithRole(CUSTOMER_ID, "ADMIN")))
                .andExpect(status().isOk());
        assertEquals(DeliveryProvisioningStatus.COMPLETE,
                orderRepository.findById(orderId).orElseThrow().getDeliveryProvisioningStatus());
        assertEquals(2, deliveryClient.calls());
    }

    @Test
    void ownerCannotSkipLifecycleAndRejectedOrderDoesNotProvisionDelivery() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "owner-rejection", validRequest())
                .andExpect(status().isCreated()).andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String ownerToken = ownerToken(CUSTOMER_ID);

        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_FOR_PICKUP\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/orders/{id}/restaurant-status", orderId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"reason\":\"Unavailable\"}"))
                .andExpect(status().isOk());
        assertEquals(OrderStatus.REJECTED, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, deliveryClient.calls());
    }

    @Test
    void deliveryCallbacksRequireDedicatedCredentialAndAreIdempotentAndMonotonic() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "delivery-callback", validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String deliveryId = "674000000000000000000001";
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setDeliveryId(deliveryId);
        order.setDeliveryProvisioningStatus(DeliveryProvisioningStatus.COMPLETE);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);

        String eventId = "pickup-event-001";
        String body = """
                {"deliveryId":"%s","eventId":"%s","status":"OUT_FOR_DELIVERY","occurredAt":"%s"}
                """.formatted(deliveryId, eventId, java.time.Instant.now());
        String token = deliveryToken("order:delivery-status:write");
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, updated.getStatus());
        assertEquals(1, updated.getDeliveryEvents().size());

        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + userToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + deliveryToken("delivery:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        String staleBody = """
                {"deliveryId":"%s","eventId":"pickup-event-stale","status":"OUT_FOR_DELIVERY","occurredAt":"%s"}
                """.formatted(deliveryId, java.time.Instant.now());
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBody))
                .andExpect(status().isConflict());
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, orderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void cancellationCallbackCompletesOnlyPendingPrePickupCancellation() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "delivery-cancel-callback", validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String deliveryId = "674000000000000000000001";
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setDeliveryId(deliveryId);
        order.setDeliveryProvisioningStatus(DeliveryProvisioningStatus.COMPLETE);
        order.setStatus(OrderStatus.PREPARING);
        order.setCancellationStatus(CancellationStatus.PENDING);
        orderRepository.save(order);

        String body = """
                {"deliveryId":"%s","eventId":"cancel-event-001","status":"CANCELLED","occurredAt":"%s"}
                """.formatted(deliveryId, java.time.Instant.now());
        String token = deliveryToken("order:delivery-status:write");
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/orders/{id}/delivery-status", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Order cancelled = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(CancellationStatus.COMPLETE, cancelled.getCancellationStatus());
        assertEquals(1, cancelled.getDeliveryEvents().size());
    }

    @Test
    void adminCannotCancelAfterPickup() throws Exception {
        MvcResult created = createOrder(CUSTOMER_ID, "admin-cancel-after-pickup", validRequest())
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        order.setDeliveryId("674000000000000000000001");
        order.setDeliveryProvisioningStatus(DeliveryProvisioningStatus.COMPLETE);
        orderRepository.save(order);

        mockMvc.perform(patch("/api/admin/orders/{id}/cancel", orderId)
                        .header("Authorization", "Bearer " + userTokenWithRole(CUSTOMER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isConflict());
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, deliveryClient.calls());
    }

    @Test
    void idempotencyUniqueIndexExistsInDedicatedDatabase() {
        var index = mongoTemplate.indexOps(Order.class).getIndexInfo().stream()
                .filter(info -> info.getName().equals("customer_idempotency_unique"))
                .findFirst()
                .orElseThrow();
        assertTrue(index.isUnique());
        assertTrue(mongoTemplate.indexOps(Order.class).getIndexInfo().stream()
                .anyMatch(info -> info.getName().equals("restaurant_history_idx")));
    }

    private org.springframework.test.web.servlet.ResultActions createOrder(
            String customerId, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken(customerId))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String validRequest() {
        return """
                {
                  "restaurantId":"%s",
                  "items":[
                    {"menuItemId":"%s","quantity":2},
                    {"menuItemId":"%s","quantity":3}
                  ],
                  "addressId":"%s"
                }
                """.formatted(RESTAURANT_ID, ITEM_ONE_ID, ITEM_TWO_ID, ADDRESS_ID);
    }

    private String changedAddressRequest() {
        return """
                {"restaurantId":"%s","items":[{"menuItemId":"%s","quantity":2},
                {"menuItemId":"%s","quantity":3}],"addressId":"671000000000000000000099"}
                """.formatted(RESTAURANT_ID, ITEM_ONE_ID, ITEM_TWO_ID);
    }

    private String userToken(String customerId) {
        return userTokenWithRole(customerId, "CUSTOMER");
    }

    private String ownerToken(String ownerId) {
        return userTokenWithRole(ownerId, "RESTAURANT_OWNER");
    }

    private String userTokenWithRole(String userId, String role) {
        var now = java.time.Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-user-service")
                .audience(List.of("foodieexpress-api"))
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String deliveryToken(String scope) {
        var now = java.time.Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodieexpress-delivery-service")
                .audience(List.of("foodieexpress-order-service"))
                .subject("delivery-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("scopes", List.of(scope))
                .build();
        JwtEncoder encoder = NimbusJwtEncoder.withKeyPair(
                (java.security.interfaces.ECPublicKey) DELIVERY_ORDER_KEYS.getPublic(),
                (java.security.interfaces.ECPrivateKey) DELIVERY_ORDER_KEYS.getPrivate()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.ES256).build(), claims)).getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClientsConfiguration {

        @Bean
        @Primary
        StubAddressLookupClient addressLookupClient() {
            return new StubAddressLookupClient();
        }

        @Bean
        @Primary
        StubRestaurantQuoteClient restaurantQuoteClient() {
            return new StubRestaurantQuoteClient();
        }

        @Bean
        @Primary
        StubRestaurantOwnerClient restaurantOwnerClient() {
            return new StubRestaurantOwnerClient();
        }

        @Bean
        @Primary
        StubDeliveryProvisioningClient deliveryProvisioningClient() {
            return new StubDeliveryProvisioningClient();
        }
    }

    static class StubAddressLookupClient implements AddressLookupClient {
        @Override
        public UserAddressResponse getOwnedAddress(String userId, String addressId) {
            return new UserAddressResponse(userId, new UserAddressResponse.Address(
                    addressId, "Customer", "10 Main St", null, "Mumbai",
                    "Maharashtra", "400001", "IN", "+919876543210"));
        }
    }

    static class StubRestaurantQuoteClient implements RestaurantQuoteClient {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile Scenario scenario = Scenario.NORMAL;

        @Override
        public RestaurantQuoteResponse quote(String restaurantId, RestaurantQuoteRequest request) {
            calls.incrementAndGet();
            if (scenario == Scenario.MISSING_ITEM) {
                throw new com.foodieexpress.orderservice.exception.ResourceNotFoundException();
            }

            if (scenario == Scenario.DEPENDENCY_FAILURE) {
                throw new com.foodieexpress.orderservice.exception.DependencyUnavailableException();
            }
            List<RestaurantQuoteResponse.Item> items = request.items().stream()
                    .map(item -> new RestaurantQuoteResponse.Item(
                            item.menuItemId(),
                            scenario == Scenario.WRONG_RESTAURANT ? "672000000000000000000099" : restaurantId,
                            item.menuItemId().equals(ITEM_ONE_ID) ? "Pasta" : "Salad",
                            item.quantity(),
                            item.menuItemId().equals(ITEM_ONE_ID) ? "12.50" : "2.35",
                            scenario == Scenario.UNSUPPORTED_CURRENCY ? "USD" : "INR",
                            scenario != Scenario.UNAVAILABLE))
                    .toList();
            return new RestaurantQuoteResponse(restaurantId, items);
        }

        int calls() {
            return calls.get();
        }

        void resetCalls() {
            calls.set(0);
        }

        void setScenario(Scenario scenario) {
            this.scenario = scenario;
        }

        enum Scenario {
            NORMAL,
            UNAVAILABLE,
            MISSING_ITEM,
            WRONG_RESTAURANT,
            UNSUPPORTED_CURRENCY,
            DEPENDENCY_FAILURE
        }
    }

    static class StubRestaurantOwnerClient implements RestaurantOwnerClient {
        private volatile boolean authorized = true;

        @Override
        public boolean isOwner(String restaurantId, String userId) {
            return authorized && RESTAURANT_ID.equalsIgnoreCase(restaurantId)
                    && CUSTOMER_ID.equalsIgnoreCase(userId);
        }

        @Override
        public RestaurantDeliveryDetailsResponse getDeliveryDetails(String restaurantId) {
            return new RestaurantDeliveryDetailsResponse(
                    restaurantId, "Restaurant", new RestaurantDeliveryDetailsResponse.Address(
                            "1 Restaurant Rd", null, "Example City", "CA", "90000", "US"));
        }

        void setAuthorized(boolean authorized) {
            this.authorized = authorized;
        }
    }

    static class StubDeliveryProvisioningClient implements DeliveryProvisioningClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private volatile boolean fail;
        private volatile DeliveryCreateRequest lastRequest;

        @Override
        public DeliveryCreateResponse create(String orderId, DeliveryCreateRequest request) {
            callCount.incrementAndGet();
            lastRequest = request;
            if (fail) {
                throw new com.foodieexpress.orderservice.exception.DependencyUnavailableException();
            }
            return new DeliveryCreateResponse(
                    "674000000000000000000001", orderId, request.restaurantId(), request.customerId(),
                    request.currency(), "PENDING_ASSIGNMENT");
        }

        @Override
        public void cancel(String deliveryId, String reason) {
        }

        void setFail(boolean fail) {
            this.fail = fail;
        }

        int calls() {
            return callCount.get();
        }

        void reset() {
            callCount.set(0);
            lastRequest = null;
        }

        DeliveryCreateRequest lastRequest() {
            return lastRequest;
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
