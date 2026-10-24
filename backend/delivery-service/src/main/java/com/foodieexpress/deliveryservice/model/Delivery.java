package com.foodieexpress.deliveryservice.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "deliveries")
@CompoundIndex(name = "delivery_order_unique", def = "{ 'orderId': 1 }", unique = true)
@CompoundIndex(name = "delivery_customer_created_idx", def = "{ 'customerId': 1, 'createdAt': -1 }")
@CompoundIndex(name = "delivery_restaurant_created_idx", def = "{ 'restaurantId': 1, 'createdAt': -1 }")
@CompoundIndex(name = "delivery_status_created_idx", def = "{ 'status': 1, 'createdAt': 1 }")
public class Delivery {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String orderId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String restaurantId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String customerId;

    private DeliveryAddress pickupAddress;
    private DeliveryAddress deliveryAddress;
    private String currency;
    private DeliveryPartner partner;
    private String assignedOperatorId;
    private DeliveryStatus status;
    private String cancellationReason;
    private List<DeliveryEvent> events;
    private boolean orderCallbackPending;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public DeliveryAddress getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(DeliveryAddress pickupAddress) { this.pickupAddress = pickupAddress; }
    public DeliveryAddress getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(DeliveryAddress deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public DeliveryPartner getPartner() { return partner; }
    public void setPartner(DeliveryPartner partner) { this.partner = partner; }
    public String getAssignedOperatorId() { return assignedOperatorId; }
    public void setAssignedOperatorId(String assignedOperatorId) { this.assignedOperatorId = assignedOperatorId; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public List<DeliveryEvent> getEvents() { return events == null ? List.of() : List.copyOf(events); }
    public void setEvents(List<DeliveryEvent> events) { this.events = List.copyOf(events); }
    public boolean isOrderCallbackPending() { return orderCallbackPending; }
    public void setOrderCallbackPending(boolean orderCallbackPending) {
        this.orderCallbackPending = orderCallbackPending;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
