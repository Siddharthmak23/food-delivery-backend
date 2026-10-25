package com.foodieexpress.restaurantservice.dto;

import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantAddress;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import java.util.List;

public record AdminRestaurantResponse(
        String id,
        String ownerUserId,
        String name,
        String description,
        List<String> cuisines,
        RestaurantAddress address,
        RestaurantStatus status) {

    public static AdminRestaurantResponse from(Restaurant restaurant) {
        return new AdminRestaurantResponse(
                restaurant.getId(),
                restaurant.getOwnerUserId(),
                restaurant.getName(),
                restaurant.getDescription(),
                List.copyOf(restaurant.getCuisines()),
                restaurant.getAddress(),
                restaurant.getStatus());
    }
}
