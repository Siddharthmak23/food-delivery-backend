package com.foodieexpress.orderservice.dto;

public record UserAddressResponse(String userId, Address address) {

    public record Address(
            String id,
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
