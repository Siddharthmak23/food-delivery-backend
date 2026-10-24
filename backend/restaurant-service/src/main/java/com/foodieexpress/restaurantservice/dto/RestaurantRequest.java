package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RestaurantRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 60) String> cuisines,
        @NotNull @Valid RestaurantAddressRequest address) {
}
