package com.foodieexpress.deliveryservice.dto;

import com.foodieexpress.deliveryservice.model.Delivery;
import com.foodieexpress.deliveryservice.model.DeliveryAddress;
import com.foodieexpress.deliveryservice.model.DeliveryEvent;
import com.foodieexpress.deliveryservice.model.DeliveryPartner;
import java.time.Instant;
import java.util.List;

public record DeliveryResponse(
        String id,
        String orderId,
        String restaurantId,
        String customerId,
        Address pickupAddress,
        Address deliveryAddress,
        Partner partner,
        String assignedOperatorId,
        String currency,
        String status,
        String cancellationReason,
        List<Event> events,
        boolean orderCallbackPending,
        Instant createdAt,
        Instant updatedAt) {

    public DeliveryResponse {
        events = List.copyOf(events);
    }

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getRestaurantId(),
                delivery.getCustomerId(),
                Address.from(delivery.getPickupAddress()),
                Address.from(delivery.getDeliveryAddress()),
                Partner.from(delivery.getPartner()),
                delivery.getAssignedOperatorId(),
                delivery.getCurrency(),
                delivery.getStatus().name(),
                delivery.getCancellationReason(),
                delivery.getEvents().stream().map(Event::from).toList(),
                delivery.isOrderCallbackPending(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }

    public record Address(
            String recipientName,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String phone) {
        private static Address from(DeliveryAddress address) {
            return new Address(address.recipientName(), address.line1(), address.line2(), address.city(),
                    address.region(), address.postalCode(), address.countryCode(), address.phone());
        }
    }

    public record Partner(String partnerId, String displayName) {
        private static Partner from(DeliveryPartner partner) {
            return partner == null ? null : new Partner(partner.partnerId(), partner.displayName());
        }
    }

    public record Event(
            String id,
            String type,
            Instant occurredAt,
            String actorId,
            String source,
            boolean callbackPending) {
        private static Event from(DeliveryEvent event) {
            return new Event(event.eventId(), event.type(), event.occurredAt(),
                    event.actorId(), event.source(), event.callbackPending());
        }
    }
}
