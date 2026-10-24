package com.foodieexpress.deliveryservice.dto;

import java.util.List;

public record DeliveryTimelineResponse(String deliveryId, List<DeliveryResponse.Event> events) {
    public DeliveryTimelineResponse {
        events = List.copyOf(events);
    }
}
