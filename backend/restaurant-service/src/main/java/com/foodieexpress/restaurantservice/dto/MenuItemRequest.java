package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MenuItemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?$") String price,
        @NotBlank @Pattern(regexp = "^USD$") String currency,
        @NotNull Boolean available) {
}
