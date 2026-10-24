package com.foodieexpress.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelOrderRequest(@NotBlank @Size(max = 500) String reason) {
}
