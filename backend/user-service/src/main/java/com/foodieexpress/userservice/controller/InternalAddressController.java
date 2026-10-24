package com.foodieexpress.userservice.controller;

import com.foodieexpress.userservice.dto.InternalAddressResponse;
import com.foodieexpress.userservice.service.AddressService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class InternalAddressController {

    private final AddressService addressService;

    public InternalAddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/internal/users/{userId}/addresses/{addressId}")
    public InternalAddressResponse getOwnedAddress(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String userId,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String addressId) {
        return addressService.getForCheckout(userId.toLowerCase(java.util.Locale.ROOT),
                addressId.toLowerCase(java.util.Locale.ROOT));
    }
}
