package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.constraints.NotNull;

public record MenuAvailabilityRequest(@NotNull Boolean available) {
}
