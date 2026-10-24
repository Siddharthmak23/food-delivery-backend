package com.foodieexpress.restaurantservice.model;

public record RestaurantAddress(
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode) {
}
