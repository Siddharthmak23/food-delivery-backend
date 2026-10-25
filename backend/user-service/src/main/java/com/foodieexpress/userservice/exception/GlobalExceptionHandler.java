package com.foodieexpress.userservice.exception;

import com.foodieexpress.userservice.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
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
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        LOGGER.warn("Malformed request body exceptionType={} causeType={}",
                exception.getClass().getSimpleName(),
                cause.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is malformed. Check the JSON fields and try again.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail() {
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email address already exists", List.of());
    }

    @ExceptionHandler(DuplicateAddressException.class)
    public ResponseEntity<ApiError> handleDuplicateAddress(DuplicateAddressException exception) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_ADDRESS", exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials() {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email or password is invalid", List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource() {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseUnavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "The user data store is unavailable", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        String traceId = UUID.randomUUID().toString();
        LOGGER.error("Unhandled request failure traceId={} exceptionType={}",
                traceId, exception.getClass().getSimpleName());
        ApiError error = new ApiError(
                java.time.Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of(),
                traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ApiError.FieldError toFieldError(FieldError error) {
        return new ApiError.FieldError(
                error.getField(),
                error.getCode() == null ? "INVALID" : error.getCode(),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), code, message, fieldErrors));
    }
}
