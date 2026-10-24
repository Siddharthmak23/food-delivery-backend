package com.foodieexpress.orderservice.exception;

import com.foodieexpress.orderservice.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiError.FieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage() {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed", List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader() {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "A required request header is missing", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied() {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", List.of());
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<ApiError> handleConflict(OrderConflictException exception) {
        return response(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ApiError> handleInvalidOrder(InvalidOrderException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ORDER", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ApiError> handleDependencyUnavailable(DependencyUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "A required service is unavailable", List.of());
    }

    @ExceptionHandler(UpstreamResponseException.class)
    public ResponseEntity<ApiError> handleUpstreamResponse(UpstreamResponseException exception) {
        return response(HttpStatus.BAD_GATEWAY, "UPSTREAM_FAILURE",
                "A required service returned an invalid response", List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure(DataAccessException exception) {
        String traceId = UUID.randomUUID().toString();
        LOGGER.warn("Order database request failed traceId={} exceptionType={}",
                traceId, exception.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "Order data is temporarily unavailable", List.of(), traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        String traceId = UUID.randomUUID().toString();
        LOGGER.error("Unhandled request failure traceId={}", traceId, exception);
        ApiError error = new ApiError(
                Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "An unexpected error occurred", List.of(), traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ApiError.FieldError toFieldError(FieldError error) {
        return new ApiError.FieldError(
                error.getField(),
                error.getCode() == null ? "INVALID" : error.getCode(),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), code, message, fieldErrors));
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiError.FieldError> fieldErrors,
            String traceId) {
        ApiError error = new ApiError(
                Instant.now(), status.value(), code, message, fieldErrors, traceId);
        return ResponseEntity.status(status).body(error);
    }
}
