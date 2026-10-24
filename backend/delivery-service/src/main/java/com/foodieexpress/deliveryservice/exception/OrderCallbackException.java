package com.foodieexpress.deliveryservice.exception;

public class OrderCallbackException extends RuntimeException {

    public OrderCallbackException() {
        super("Order Service callback could not be completed");
    }
}
