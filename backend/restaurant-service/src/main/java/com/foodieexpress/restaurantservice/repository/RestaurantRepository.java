package com.foodieexpress.restaurantservice.repository;

import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RestaurantRepository extends MongoRepository<Restaurant, String> {

    Optional<Restaurant> findByIdAndStatus(String id, RestaurantStatus status);

    Optional<Restaurant> findByIdAndOwnerUserId(String id, String ownerUserId);

    Page<Restaurant> findAllByOwnerUserId(String ownerUserId, Pageable pageable);

    long countByOwnerUserId(String ownerUserId);

    Page<Restaurant> findAllByStatus(RestaurantStatus status, Pageable pageable);
}
