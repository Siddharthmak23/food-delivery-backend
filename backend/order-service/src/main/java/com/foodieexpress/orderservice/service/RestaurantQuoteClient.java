package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.RestaurantQuoteRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteResponse;

public interface RestaurantQuoteClient {

    RestaurantQuoteResponse quote(String restaurantId, RestaurantQuoteRequest request);
}
