package com.foodieexpress.orderservice.exception;

public class UpstreamResponseException extends RuntimeException {

    public UpstreamResponseException() {
        super("A required service returned an invalid response");
    }
}
