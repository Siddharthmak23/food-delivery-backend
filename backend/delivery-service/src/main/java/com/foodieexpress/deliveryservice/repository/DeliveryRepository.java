package com.foodieexpress.deliveryservice.repository;

import com.foodieexpress.deliveryservice.model.Delivery;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    Optional<Delivery> findByOrderId(String orderId);

    Optional<Delivery> findByIdAndCustomerId(String id, String customerId);

    Page<Delivery> findAllByAssignedOperatorId(String assignedOperatorId, Pageable pageable);
}
