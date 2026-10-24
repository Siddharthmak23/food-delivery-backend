package com.foodieexpress.userservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(
            int status,
            String code,
            String message,
            List<FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status,
                code,
                message,
                fieldErrors,
                UUID.randomUUID().toString());
    }

    public record FieldError(String field, String code, String message) {
    }
}
