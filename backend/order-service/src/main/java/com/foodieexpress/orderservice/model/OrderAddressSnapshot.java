package com.foodieexpress.orderservice.model;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record OrderAddressSnapshot(
        @Field(targetType = FieldType.OBJECT_ID) String addressId,
        String recipientName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        String phone) {
}
