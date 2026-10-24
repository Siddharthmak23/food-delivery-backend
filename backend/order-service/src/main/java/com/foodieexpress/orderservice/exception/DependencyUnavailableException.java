package com.foodieexpress.orderservice.exception;

public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException() {
        super("A required service is unavailable");
    }
}
