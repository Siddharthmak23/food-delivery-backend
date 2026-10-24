package com.foodieexpress.orderservice.dto;

import com.foodieexpress.orderservice.model.Order;
import com.foodieexpress.orderservice.model.OrderItemSnapshot;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String restaurantId,
        List<OrderItemResponse> items,
        OrderAddressResponse deliveryAddress,
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
        String cancellationReason,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        Instant statusUpdatedAt) {

    public OrderResponse {
        items = List.copyOf(items);
    }

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderResponse::item)
                .toList();
        var address = order.getDeliveryAddress();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                items,
                new OrderAddressResponse(
                        address.addressId(), address.recipientName(), address.line1(), address.line2(),
                        address.city(), address.region(), address.postalCode(), address.countryCode(), address.phone()),
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
                order.getCancellationReason(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getStatusUpdatedAt());
    }

    private static OrderItemResponse item(OrderItemSnapshot item) {
        return new OrderItemResponse(
                item.menuItemId(), item.name(), item.quantity(),
                item.unitPrice().toPlainString(), item.lineTotal().toPlainString());
    }
}
