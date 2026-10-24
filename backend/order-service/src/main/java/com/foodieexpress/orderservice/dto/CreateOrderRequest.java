package com.foodieexpress.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreateOrderRequest {

    @NotBlank
    @Pattern(regexp = "^[a-fA-F0-9]{24}$")
    private String restaurantId;

    @NotEmpty
    @Size(max = 50)
    private List<@Valid CreateOrderItemRequest> items;

    @NotBlank
    @Pattern(regexp = "^[a-fA-F0-9]{24}$")
    private String addressId;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported order request field: " + name);
    }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }
    public List<CreateOrderItemRequest> getItems() { return items; }
    public void setItems(List<CreateOrderItemRequest> items) { this.items = items; }
    public String getAddressId() { return addressId; }
    public void setAddressId(String addressId) { this.addressId = addressId; }
}
