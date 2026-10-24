package com.foodieexpress.restaurantservice.dto;

import java.util.List;

public record MenuQuoteResponse(String restaurantId, List<Item> items) {

    public MenuQuoteResponse {
        items = List.copyOf(items);
    }

    public record Item(
            String id,
            String restaurantId,
            String name,
            int quantity,
            String price,
            String currency,
            boolean available) {
    }
}
