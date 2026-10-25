package com.foodieexpress.orderservice.dto;

import com.foodieexpress.orderservice.model.Order;
import java.time.Instant;
import java.util.List;

public record AdminOrderResponse(
        String id,
        String customerId,
        String restaurantId,
        List<OrderItemResponse> items,
        String currency,
        String subtotal,
        String deliveryFee,
        String discount,
        String total,
        String paymentStatus,
        String status,
        String deliveryProvisioningStatus,
        String deliveryId,
        String cancellationStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant statusUpdatedAt) {

    public AdminOrderResponse {
        items = List.copyOf(items);
    }

    public static AdminOrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.menuItemId(), item.name(), item.quantity(),
                        item.unitPrice().toPlainString(), item.lineTotal().toPlainString()))
                .toList();
        return new AdminOrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                items,
                order.getCurrency(),
                order.getSubtotal().toPlainString(),
                order.getDeliveryFee().toPlainString(),
                order.getDiscount().toPlainString(),
                order.getTotal().toPlainString(),
                order.getPaymentStatus().name(),
                order.getStatus().name(),
                order.getDeliveryProvisioningStatus().name(),
                order.getDeliveryId(),
                order.getCancellationStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getStatusUpdatedAt());
    }
}
