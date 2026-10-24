package com.foodieexpress.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "orders")
@CompoundIndex(name = "customer_history_idx", def = "{ 'customerId': 1, 'createdAt': -1, '_id': -1 }")
@CompoundIndex(name = "restaurant_history_idx", def = "{ 'restaurantId': 1, 'createdAt': 1, '_id': 1 }")
@CompoundIndex(name = "restaurant_status_created_idx", def = "{ 'restaurantId': 1, 'status': 1, 'createdAt': 1 }")
@CompoundIndex(name = "status_created_idx", def = "{ 'status': 1, 'createdAt': 1 }")
@CompoundIndex(
        name = "customer_idempotency_unique",
        def = "{ 'customerId': 1, 'idempotency.key': 1 }",
        unique = true)
public class Order {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String customerId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String restaurantId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String deliveryId;

    private List<OrderItemSnapshot> items;
    private List<OrderDeliveryEvent> deliveryEvents = List.of();
    private OrderAddressSnapshot deliveryAddress;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discount;
    private BigDecimal total;
    private PaymentStatus paymentStatus;
    private OrderStatus status;
    private DeliveryProvisioningStatus deliveryProvisioningStatus;
    private CancellationStatus cancellationStatus;
    private String cancellationReason;
    private String rejectionReason;
    private OrderIdempotency idempotency;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant statusUpdatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public List<OrderItemSnapshot> getItems() { return items; }
    public void setItems(List<OrderItemSnapshot> items) { this.items = List.copyOf(items); }
    public List<OrderDeliveryEvent> getDeliveryEvents() {
        return deliveryEvents == null ? List.of() : List.copyOf(deliveryEvents);
    }
    public void setDeliveryEvents(List<OrderDeliveryEvent> deliveryEvents) {
        this.deliveryEvents = List.copyOf(deliveryEvents);
    }
    public OrderAddressSnapshot getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(OrderAddressSnapshot deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public DeliveryProvisioningStatus getDeliveryProvisioningStatus() { return deliveryProvisioningStatus; }
    public void setDeliveryProvisioningStatus(DeliveryProvisioningStatus status) {
        this.deliveryProvisioningStatus = status;
    }
    public CancellationStatus getCancellationStatus() { return cancellationStatus; }
    public void setCancellationStatus(CancellationStatus cancellationStatus) {
        this.cancellationStatus = cancellationStatus;
    }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public OrderIdempotency getIdempotency() { return idempotency; }
    public void setIdempotency(OrderIdempotency idempotency) { this.idempotency = idempotency; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(Instant statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }
}
