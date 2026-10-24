package com.foodieexpress.restaurantservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodieexpress.restaurantservice.model.MenuItem;
import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantAddress;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import com.foodieexpress.restaurantservice.repository.MenuItemRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@EnabledIfSystemProperty(named = "restaurant.mongo.integration", matches = "true")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=500",
        "spring.mongodb.database=food_restaurant_phase4_test"
})
@AutoConfigureMockMvc
class RestaurantMongoIntegrationTests {

    private static final String JWT_SECRET = generateTestSecret();
    private static final KeyPair INTERNAL_KEY_PAIR = generateInternalKeyPair();
    private static final String OWNER_ID = "671000000000000000000001";
    private static final String OTHER_OWNER_ID = "671000000000000000000002";

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        registry.add("ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64",
                () -> Base64.getEncoder().encodeToString(INTERNAL_KEY_PAIR.getPublic().getEncoded()));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @BeforeEach
    void prepareDatabase() {
        mongoTemplate.dropCollection(Restaurant.class);
        mongoTemplate.dropCollection(MenuItem.class);
        mongoTemplate.indexOps(Restaurant.class).ensureIndex(
                new TextIndexDefinition.TextIndexDefinitionBuilder()
                        .onField("name")
                        .onField("description")
                        .build());
        seedRestaurant("Nori House", "Fresh Japanese bowls", List.of("Japanese"), RestaurantStatus.ACTIVE);
        seedRestaurant("Nori Garden", "Japanese garden cafe", List.of("Japanese"), RestaurantStatus.ACTIVE);
        seedRestaurant("Hidden Nori", "Pending Japanese restaurant", List.of("Japanese"),
                RestaurantStatus.PENDING_APPROVAL);
        Restaurant italian = seedRestaurant(
                "Pasta Place", "Italian pasta", List.of("Italian"), RestaurantStatus.ACTIVE);
        addMenuItem(italian.getId(), "Pasta", "12.50", true);
        addMenuItem(italian.getId(), "Seasonal Soup", "5.00", false);
    }

    @AfterEach
    void cleanDatabase() {
        mongoTemplate.dropCollection(Restaurant.class);
        mongoTemplate.dropCollection(MenuItem.class);
    }

    @Test
    void publicDiscoveryAndMenuHideUnapprovedRestaurantAndReturnCurrentPrices() throws Exception {
        MvcResult page = mockMvc.perform(get("/api/restaurants")
                        .param("q", "nori")
                        .param("cuisine", "Japanese")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        String pageBody = page.getResponse().getContentAsString();
        assertTrue(pageBody.contains("\"totalItems\":2"));
        assertTrue(pageBody.contains("\"size\":1"));
        assertTrue(pageBody.contains("\"sort\":\"name,asc\""));
        assertTrue(pageBody.contains("Nori Garden"));
        assertFalse(pageBody.contains("Hidden Nori"));

        Restaurant italian = restaurantRepository.findAll().stream()
                .filter(restaurant -> restaurant.getName().equals("Pasta Place"))
                .findFirst()
                .orElseThrow();
        MvcResult menu = mockMvc.perform(get("/api/restaurants/{id}/menu", italian.getId())
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(menu.getResponse().getContentAsString().contains("\"price\":\"12.50\""));
        assertFalse(menu.getResponse().getContentAsString().contains("Seasonal Soup"));

        mockMvc.perform(get("/api/restaurants/{id}", "ffffffffffffffffffffffff"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/restaurants/{id}/menu", "ffffffffffffffffffffffff"))
                .andExpect(status().isNotFound());
        MenuItem unavailable = menuItemRepository.findAll().stream()
                .filter(item -> item.getName().equals("Seasonal Soup"))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(get("/api/menu-items/{id}", unavailable.getId()))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(get("/api/menu-items/{id}", "eeeeeeeeeeeeeeeeeeeeeeee"))
                .andExpect(status().isNotFound());

        MenuItem available = menuItemRepository.findAll().stream()
                .filter(item -> item.getName().equals("Pasta"))
                .findFirst()
                .orElseThrow();
        MvcResult quote = mockMvc.perform(post("/internal/restaurants/{id}/menu-quote", italian.getId())
                        .header("Authorization", "Bearer " + internalToken("menu:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"menuItemId":"%s","quantity":3},
                                    {"menuItemId":"%s","quantity":1}
                                  ]
                                }
                                """.formatted(available.getId(), unavailable.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String quoteBody = quote.getResponse().getContentAsString();
        assertTrue(quoteBody.contains("\"price\":\"12.50\""));
        assertTrue(quoteBody.contains("\"quantity\":3"));
        assertTrue(quoteBody.contains("\"available\":false"));

        mockMvc.perform(post("/internal/restaurants/{id}/menu-quote", italian.getId())
                        .header("Authorization", "Bearer " + internalToken("menu:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"menuItemId":"%s","quantity":0}]}
                                """.formatted(available.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/restaurants/{id}/menu-quote", italian.getId())
                        .header("Authorization", "Bearer " + internalToken("menu:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"menuItemId":"eeeeeeeeeeeeeeeeeeeeeeee","quantity":1}]}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/restaurants/{id}/menu-quote", "ffffffffffffffffffffffff")
                        .header("Authorization", "Bearer " + internalToken("menu:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"menuItemId":"%s","quantity":1}]}
                                """.formatted(available.getId())))
                .andExpect(status().isNotFound());

        MvcResult owner = mockMvc.perform(
                        get("/internal/restaurants/{restaurantId}/owners/{userId}", italian.getId(), OWNER_ID)
                                .header("Authorization", "Bearer " + internalToken("restaurant:ownership:read")))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(owner.getResponse().getContentAsString().contains("\"isOwner\":true"));
        mockMvc.perform(
                        get("/internal/restaurants/{restaurantId}/owners/{userId}", italian.getId(), OTHER_OWNER_ID)
                                .header("Authorization", "Bearer "
                                        + internalToken("restaurant:ownership:read")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/internal/restaurants/{restaurantId}/owners/{userId}",
                        "ffffffffffffffffffffffff", OWNER_ID)
                        .header("Authorization", "Bearer " + internalToken("restaurant:ownership:read")))
                .andExpect(status().isNotFound());
        MvcResult deliveryDetails = mockMvc.perform(
                        get("/internal/restaurants/{id}/delivery-details", italian.getId())
                                .header("Authorization", "Bearer " + internalToken("delivery:provision")))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(deliveryDetails.getResponse().getContentAsString().contains("\"name\":\"Pasta Place\""));
        assertTrue(deliveryDetails.getResponse().getContentAsString().contains("\"line1\":\"1 Main St\""));
    }

    @Test
    void ownerCanCreatePendingRestaurantAndMenuAndAdminCanApproveIt() throws Exception {
        String request = """
                {
                  "name":"Owner Kitchen",
                  "description":"Local food",
                  "cuisines":["American"],
                  "address":{
                    "line1":"10 Main St",
                    "city":"Example City",
                    "region":"CA",
                    "postalCode":"90000",
                    "countryCode":"US"
                  }
                }
                """;
        MvcResult created = mockMvc.perform(post("/api/restaurants")
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        String response = created.getResponse().getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([a-fA-F0-9]{24})\"").matcher(response);
        assertTrue(idMatcher.find());
        String restaurantId = idMatcher.group(1);
        assertTrue(response.contains("\"status\":\"PENDING_APPROVAL\""));
        assertFalse(response.contains("ownerUserId"));

        mockMvc.perform(get("/api/restaurants/{id}", restaurantId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/restaurants/{id}/menu-items", restaurantId)
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Invalid Plate",
                                  "category":"Main",
                                  "price":"-5.00",
                                  "currency":"USD",
                                  "available":true
                                }
                                """))
                .andExpect(status().isBadRequest());

        MvcResult createdItem = mockMvc.perform(post("/api/restaurants/{id}/menu-items", restaurantId)
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"House Plate",
                                  "description":"Daily special",
                                  "category":"Main",
                                  "price":"10.5",
                                  "currency":"USD",
                                  "available":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Matcher menuItemIdMatcher = Pattern.compile("\"id\":\"([a-fA-F0-9]{24})\"")
                .matcher(createdItem.getResponse().getContentAsString());
        assertTrue(menuItemIdMatcher.find());
        String menuItemId = menuItemIdMatcher.group(1);

        mockMvc.perform(put("/api/menu-items/{id}", menuItemId)
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Updated Plate",
                                  "description":"Updated",
                                  "category":"Main",
                                  "price":"11.00",
                                  "currency":"USD",
                                  "available":true
                                }
                                """))
                .andExpect(status().isOk());
        MvcResult availability = mockMvc.perform(patch("/api/menu-items/{id}/availability", menuItemId)
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(availability.getResponse().getContentAsString().contains("\"available\":false"));

        mockMvc.perform(put("/api/restaurants/{id}", restaurantId)
                        .header("Authorization", "Bearer " + token(OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/restaurants/{id}", restaurantId)
                        .header("Authorization", "Bearer " + token(OTHER_OWNER_ID, "RESTAURANT_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/restaurants/{id}/approval", restaurantId)
                        .header("Authorization", "Bearer " + token(OTHER_OWNER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/restaurants/{id}", restaurantId))
                .andExpect(status().isOk());
        MvcResult adminList = mockMvc.perform(get("/api/admin/restaurants")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token(OTHER_OWNER_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(adminList.getResponse().getContentAsString().contains("Owner Kitchen"));
    }

    private Restaurant seedRestaurant(
            String name, String description, List<String> cuisines, RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setOwnerUserId(OWNER_ID);
        restaurant.setName(name);
        restaurant.setDescription(description);
        restaurant.setCuisines(cuisines);
        restaurant.setAddress(new RestaurantAddress("1 Main St", null, "Example City", "CA", "90000", "US"));
        restaurant.setStatus(status);
        restaurant.setCreatedAt(Instant.now());
        restaurant.setUpdatedAt(Instant.now());
        return restaurantRepository.save(restaurant);
    }

    private void addMenuItem(String restaurantId, String name, String price, boolean available) {
        MenuItem item = new MenuItem();
        item.setRestaurantId(restaurantId);
        item.setName(name);
        item.setCategory("Main");
        item.setPrice(new java.math.BigDecimal(price));
        item.setCurrency("USD");
        item.setAvailable(available);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        menuItemRepository.save(item);
    }

    private String token(String subject, String role) throws Exception {
        Instant now = Instant.now();
        String claims = """
                {"iss":"foodieexpress-user-service","aud":["foodieexpress-api"],"sub":"%s",\
                "iat":%d,"exp":%d,"roles":["%s"]}
                """.formatted(subject, now.getEpochSecond(), now.plusSeconds(300).getEpochSecond(), role);
        JWSObject signed = new JWSObject(
                new JWSHeader(JWSAlgorithm.HS256),
                new Payload(claims.getBytes(StandardCharsets.UTF_8)));
        signed.sign(new MACSigner(Base64.getDecoder().decode(JWT_SECRET)));
        return signed.serialize();
    }

    private String internalToken(String scope) {
        Instant now = Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
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
