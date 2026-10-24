package com.foodieexpress.orderservice.repository;

import com.foodieexpress.orderservice.model.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface OrderRepository extends MongoRepository<Order, String> {

    @Query("{ 'customerId': ?0, 'idempotency.key': ?1 }")
    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String key);

    Optional<Order> findByIdAndCustomerId(String id, String customerId);

    Page<Order> findAllByCustomerId(String customerId, Pageable pageable);

    Page<Order> findAllByRestaurantId(String restaurantId, Pageable pageable);
}
