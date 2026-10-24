package com.foodieexpress.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestaurantOrderStatusRequest(
        @NotBlank @Pattern(regexp = "^(CONFIRMED|REJECTED|PREPARING|READY_FOR_PICKUP)$") String status,
        @Size(max = 500) String reason) {
}
