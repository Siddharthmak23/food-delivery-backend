package com.foodieexpress.orderservice.model;

import java.time.Instant;

public record OrderDeliveryEvent(String eventId, String deliveryId, String status, Instant occurredAt) {
}
