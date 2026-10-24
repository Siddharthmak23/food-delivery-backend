package com.foodieexpress.restaurantservice.dto;

import com.foodieexpress.restaurantservice.model.MenuItem;
import java.math.BigDecimal;

public record MenuItemResponse(
        String id,
        String restaurantId,
        String name,
        String description,
        String category,
        String price,
        String currency,
        boolean available) {

    public static MenuItemResponse from(MenuItem menuItem) {
        BigDecimal normalizedPrice = menuItem.getPrice().setScale(2);
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getRestaurantId(),
                menuItem.getName(),
                menuItem.getDescription(),
                menuItem.getCategory(),
                normalizedPrice.toPlainString(),
                menuItem.getCurrency(),
                menuItem.isAvailable());
    }
}
