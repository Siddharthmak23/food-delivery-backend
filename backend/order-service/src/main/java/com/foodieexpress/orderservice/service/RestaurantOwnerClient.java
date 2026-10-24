package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.RestaurantDeliveryDetailsResponse;

public interface RestaurantOwnerClient {

    boolean isOwner(String restaurantId, String userId);

    RestaurantDeliveryDetailsResponse getDeliveryDetails(String restaurantId);
}
