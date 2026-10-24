package com.foodieexpress.orderservice.dto;

import java.util.List;

public record RestaurantQuoteResponse(String restaurantId, List<Item> items) {
    public record Item(
            String id,
            String restaurantId,
            String name,
            Integer quantity,
            String price,
            String currency,
            Boolean available) {
    }
}
