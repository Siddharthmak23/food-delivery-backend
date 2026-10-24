package com.foodieexpress.restaurantservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MenuQuoteRequest(
        @NotEmpty @Size(max = 50) List<@Valid MenuQuoteItemRequest> items) {
}
