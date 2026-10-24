package com.foodieexpress.orderservice.dto;

public record OrderAddressResponse(
        String addressId,
        String recipientName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        String phone) {
}
