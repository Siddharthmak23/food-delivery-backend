package com.foodieexpress.orderservice.model;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record OrderItemSnapshot(
        @Field(targetType = FieldType.OBJECT_ID) String menuItemId,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
