package com.foodieexpress.orderservice.dto;

public record DeliveryCreateResponse(
        String id,
        String orderId,
        String restaurantId,
        String customerId,
        String currency,
        String status) {
}
