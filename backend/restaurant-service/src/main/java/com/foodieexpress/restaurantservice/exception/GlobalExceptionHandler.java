package com.foodieexpress.restaurantservice.exception;

import com.foodieexpress.restaurantservice.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter() {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "A request parameter is invalid", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingBusinessResource() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey() {
        return response(HttpStatus.CONFLICT, "CONFLICT", "The requested resource conflicts with existing data",
                List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseUnavailable(DataAccessException exception) {
        String traceId = UUID.randomUUID().toString();
        LOGGER.warn("MongoDB request failed traceId={} exceptionType={}",
                traceId, exception.getClass().getSimpleName());
        ApiError error = new ApiError(
                Instant.now(), HttpStatus.SERVICE_UNAVAILABLE.value(), "DEPENDENCY_UNAVAILABLE",
                "The data store is temporarily unavailable", List.of(), traceId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
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
}
