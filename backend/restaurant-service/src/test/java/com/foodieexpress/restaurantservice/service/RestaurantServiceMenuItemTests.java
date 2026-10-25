package com.foodieexpress.restaurantservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodieexpress.restaurantservice.dto.MenuItemRequest;
import com.foodieexpress.restaurantservice.exception.ResourceNotFoundException;
import com.foodieexpress.restaurantservice.model.MenuItem;
import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.repository.MenuItemRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantQueryRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RestaurantServiceMenuItemTests {

    private static final String OWNER_ID = "671000000000000000000001";
    private static final String OTHER_OWNER_ID = "671000000000000000000002";
    private static final String RESTAURANT_ID = "672000000000000000000001";
    private static final String ITEM_ID = "672000000000000000000010";

    @Test
    void explicitlyRepricesOwnedLegacyUsdItemAndPersistsInrPriceAndCurrency() {
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        MenuItemRepository menuItems = mock(MenuItemRepository.class);
        RestaurantService service = new RestaurantService(
                restaurants, menuItems, mock(RestaurantQueryRepository.class));
        MenuItem legacy = legacyItem();
        when(menuItems.findById(ITEM_ID)).thenReturn(Optional.of(legacy));
        when(restaurants.findByIdAndOwnerUserId(RESTAURANT_ID, OWNER_ID))
                .thenReturn(Optional.of(ownedRestaurant()));
        when(menuItems.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MenuItem updated = service.updateMenuItem(OWNER_ID, ITEM_ID,
                new MenuItemRequest("Legacy meal", null, "Main", "250.00", "INR", true));

        assertEquals(new BigDecimal("250.00"), updated.getPrice());
        assertEquals("INR", updated.getCurrency());
        assertEquals(new BigDecimal("250.00"), legacy.getPrice());
        assertEquals("INR", legacy.getCurrency());
        verify(menuItems).save(legacy);
    }

    @Test
    void doesNotUpdateMenuItemWhenCallerDoesNotOwnItsRestaurant() {
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        MenuItemRepository menuItems = mock(MenuItemRepository.class);
        RestaurantService service = new RestaurantService(
                restaurants, menuItems, mock(RestaurantQueryRepository.class));
        MenuItem legacy = legacyItem();
        when(menuItems.findById(ITEM_ID)).thenReturn(Optional.of(legacy));
        when(restaurants.findByIdAndOwnerUserId(RESTAURANT_ID, OTHER_OWNER_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateMenuItem(
                OTHER_OWNER_ID, ITEM_ID,
                new MenuItemRequest("Unauthorized", null, "Main", "1.00", "INR", true)));

        assertEquals(new BigDecimal("10.00"), legacy.getPrice());
        assertEquals("USD", legacy.getCurrency());
        verify(menuItems, never()).save(any(MenuItem.class));
    }

    private MenuItem legacyItem() {
        MenuItem item = new MenuItem();
        item.setId(ITEM_ID);
        item.setRestaurantId(RESTAURANT_ID);
        item.setName("Legacy meal");
        item.setCategory("Main");
        item.setPrice(new BigDecimal("10.00"));
        item.setCurrency("USD");
        item.setAvailable(true);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    private Restaurant ownedRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setOwnerUserId(OWNER_ID);
        return restaurant;
    }
}
