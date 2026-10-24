package com.foodieexpress.orderservice.dto;

public record DeliveryCreateRequest(
        String orderId,
        String restaurantId,
        String customerId,
        Address pickupAddress,
        Address deliveryAddress,
        String currency) {

    public record Address(
            String recipientName,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone) {
    }
}
