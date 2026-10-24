package com.foodieexpress.restaurantservice.dto;

import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantAddress;

public record RestaurantDeliveryDetailsResponse(String id, String name, RestaurantAddress address) {

    public static RestaurantDeliveryDetailsResponse from(Restaurant restaurant) {
        return new RestaurantDeliveryDetailsResponse(
                restaurant.getId(), restaurant.getName(), restaurant.getAddress());
    }
}
