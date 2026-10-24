package com.foodieexpress.orderservice.model;

public record OrderIdempotency(String key, String requestFingerprint) {
}
