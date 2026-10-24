package com.foodieexpress.deliveryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateDeliveryRequest(
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{24}$") String orderId,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{24}$") String restaurantId,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{24}$") String customerId,
        @NotNull @Valid PickupAddressRequest pickupAddress,
        @NotNull @Valid DeliveryAddressRequest deliveryAddress,
        @NotBlank @Pattern(regexp = "^USD$") String currency) {
}
