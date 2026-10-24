package com.foodieexpress.orderservice.dto;

public record OrderItemResponse(
        String menuItemId,
        String name,
        int quantity,
        String unitPrice,
        String lineTotal) {
}
