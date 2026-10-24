package com.foodieexpress.deliveryservice.dto;

import com.foodieexpress.deliveryservice.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStatusRequest(@NotNull DeliveryStatus status) {
}
