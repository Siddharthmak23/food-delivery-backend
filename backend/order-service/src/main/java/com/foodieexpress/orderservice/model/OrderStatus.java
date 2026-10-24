package com.foodieexpress.orderservice.model;

public enum OrderStatus {
    PLACED,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED
}
