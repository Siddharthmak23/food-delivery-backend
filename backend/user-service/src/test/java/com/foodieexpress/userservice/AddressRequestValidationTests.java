package com.foodieexpress.userservice;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodieexpress.userservice.dto.AddressRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AddressRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCompleteIndianAddressAndFormattedIndianMobileNumbers() {
        assertTrue(validator.validate(validAddress("9876543210")).isEmpty());
        assertTrue(validator.validate(validAddress("+919876543210")).isEmpty());
        assertTrue(validator.validate(validAddress("+91 98765-43210")).isEmpty());
        assertTrue(validator.validate(validAddress("98765 43210")).isEmpty());
    }

    @Test
    void rejectsInvalidIndianMobileNumberLengthsCharactersAndPrefixes() {
        for (String phone : Set.of(
                "987654321", "98765432101", "98765abc10", "+1 9876543210", "+91 98765")) {
            assertTrue(validator.validate(validAddress(phone)).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().equals("phone")), phone);
        }
    }

    @Test
    void rejectsUnsupportedCountryRegionPostalCodeAndPhone() {
        AddressRequest invalid = new AddressRequest(
                "Home", "Customer", "10 Main Road", null, "Mumbai", "California",
                "40001", "US", "+14155550123");

        Set<String> invalidFields = validator.validate(invalid).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.containsAll(Set.of("region", "postalCode", "countryCode", "phone")));
    }

    @Test
    void requiresRecipientPhoneAddressCityRegionAndPostalCode() {
        AddressRequest incomplete = new AddressRequest(
                "Home", " ", " ", null, " ", "", "", "IN", "");

        Set<String> invalidFields = validator.validate(incomplete).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.containsAll(Set.of(
                "recipientName", "phone", "line1", "city", "region", "postalCode")));
    }

    private AddressRequest validAddress(String phone) {
        return new AddressRequest(
                "Home", "Customer", "10 Main Road", null, "Mumbai", "Maharashtra",
                "400001", "IN", phone);
    }
}
