package com.foodieexpress.restaurantservice.dto;

import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import jakarta.validation.constraints.NotNull;

public record RestaurantApprovalRequest(@NotNull RestaurantStatus status) {
}
