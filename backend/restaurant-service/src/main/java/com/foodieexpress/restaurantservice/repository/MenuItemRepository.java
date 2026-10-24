package com.foodieexpress.restaurantservice.repository;

import com.foodieexpress.restaurantservice.model.MenuItem;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MenuItemRepository extends MongoRepository<MenuItem, String> {

    Optional<MenuItem> findByIdAndRestaurantId(String id, String restaurantId);
}
