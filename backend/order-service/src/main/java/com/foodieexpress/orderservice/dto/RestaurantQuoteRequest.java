package com.foodieexpress.orderservice.dto;

import java.util.List;

public record RestaurantQuoteRequest(List<Item> items) {
    public RestaurantQuoteRequest {
        items = List.copyOf(items);
    }

    public record Item(String menuItemId, int quantity) {
    }
}
