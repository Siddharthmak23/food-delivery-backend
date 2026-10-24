package com.foodieexpress.restaurantservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "menuItems")
@CompoundIndex(
        name = "menu_restaurant_available_name_idx",
        def = "{ 'restaurantId': 1, 'available': 1, 'name': 1, '_id': 1 }")
@CompoundIndex(name = "menu_item_restaurant_idx", def = "{ '_id': 1, 'restaurantId': 1 }")
public class MenuItem {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String restaurantId;

    private String name;
    private String description;
    private String category;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    private String currency;
    private boolean available;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
