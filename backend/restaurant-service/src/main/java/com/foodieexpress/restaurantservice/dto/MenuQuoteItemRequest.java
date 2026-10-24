package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MenuQuoteItemRequest(
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{24}$") String menuItemId,
        @NotNull @Min(1) @Max(100) Integer quantity) {
}
