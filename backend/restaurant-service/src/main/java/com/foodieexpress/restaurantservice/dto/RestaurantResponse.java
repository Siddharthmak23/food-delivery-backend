package com.foodieexpress.restaurantservice.dto;

import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantAddress;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import java.util.List;

public record RestaurantResponse(
        String id,
        String name,
        String description,
        List<String> cuisines,
        RestaurantAddress address,
        RestaurantStatus status) {

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                List.copyOf(restaurant.getCuisines()),
                restaurant.getAddress(),
                restaurant.getStatus());
    }
}
