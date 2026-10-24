package com.foodieexpress.restaurantservice.dto;

public record RestaurantOwnerVerificationResponse(String restaurantId, String userId, boolean isOwner) {
}
