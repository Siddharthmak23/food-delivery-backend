package com.foodieexpress.restaurantservice.repository;

import com.foodieexpress.restaurantservice.model.MenuItem;
import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RestaurantQueryRepository {

    Page<Restaurant> discover(String query, String cuisine, RestaurantStatus status, Pageable pageable);

    Page<MenuItem> findMenuItems(String restaurantId, Boolean available, Pageable pageable);
}
