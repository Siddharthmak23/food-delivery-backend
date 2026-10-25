package com.foodieexpress.userservice.exception;

public class DuplicateAddressException extends RuntimeException {
    public DuplicateAddressException() {
        super("This delivery address is already saved.");
    }
}
