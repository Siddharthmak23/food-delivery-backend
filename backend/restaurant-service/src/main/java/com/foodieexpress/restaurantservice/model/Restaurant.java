package com.foodieexpress.restaurantservice.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "restaurants")
@CompoundIndex(name = "restaurant_status_name_idx", def = "{ 'status': 1, 'name': 1, '_id': 1 }")
@CompoundIndex(name = "restaurant_owner_status_idx", def = "{ 'ownerUserId': 1, 'status': 1 }")
public class Restaurant {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerUserId;

    @TextIndexed
    private String name;

    @TextIndexed
    private String description;

    @Indexed(name = "cuisines_idx")
    private List<String> cuisines;

    private RestaurantAddress address;
    private RestaurantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getCuisines() { return cuisines; }
    public void setCuisines(List<String> cuisines) { this.cuisines = cuisines; }
    public RestaurantAddress getAddress() { return address; }
    public void setAddress(RestaurantAddress address) { this.address = address; }
    public RestaurantStatus getStatus() { return status; }
    public void setStatus(RestaurantStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
