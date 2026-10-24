package com.foodieexpress.orderservice.dto;

public record RestaurantDeliveryDetailsResponse(String id, String name, Address address) {
    public record Address(
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode) {
    }
}
