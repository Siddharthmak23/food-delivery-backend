package com.foodieexpress.deliveryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelDeliveryRequest(@NotBlank @Size(max = 300) String reason) {
}
