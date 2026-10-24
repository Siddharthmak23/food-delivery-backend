package com.foodieexpress.deliveryservice.model;

import java.time.Instant;

public record DeliveryEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String actorId,
        String source,
        boolean callbackPending) {
}
