package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestaurantAddressRequest(
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String region,
        @NotBlank @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
}
