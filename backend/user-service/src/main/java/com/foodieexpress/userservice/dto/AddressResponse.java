package com.foodieexpress.userservice.dto;

import com.foodieexpress.userservice.model.Address;
import java.time.Instant;

public record AddressResponse(
        String id,
        String label,
        String recipientName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        String phone,
        Instant createdAt,
        Instant updatedAt) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.getPhone(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
