package com.foodieexpress.deliveryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignDeliveryRequest(@NotBlank @Size(max = 64) String partnerId) {
}
