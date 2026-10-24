package com.foodieexpress.deliveryservice.model;

public record DeliveryAddress(
        String recipientName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        String phone) {
}
