package com.foodieexpress.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record DeliveryStatusCallbackRequest(
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{24}$") String deliveryId,
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Pattern(regexp = "^(OUT_FOR_DELIVERY|DELIVERED|CANCELLED)$") String status,
        @NotNull Instant occurredAt) {
}
