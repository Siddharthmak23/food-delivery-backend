package com.foodieexpress.userservice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.foodieexpress.userservice.dto.UpdateProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateProfileRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "9876543210", "+919876543210", "+91 98765-43210"})
    void acceptsOptionalEmptyOrValidIndianMobileNumber(String phone) {
        assertFalse(validator.validate(new UpdateProfileRequest("Customer", phone)).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("phone")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "333333333333", "3333333333", "1234567890", "987654321", "98765432101",
            "98765abc10", "+1 9876543210", "+91 3333333333", "+91 6666666666"
    })
    void rejectsInvalidIndianMobileNumbers(String phone) {
        assertTrue(validator.validate(new UpdateProfileRequest("Customer", phone)).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("phone")), phone);
    }
}
