package com.foodieexpress.userservice.repository;

import com.foodieexpress.userservice.model.Address;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AddressRepository extends MongoRepository<Address, String> {

    Page<Address> findAllByUserId(String userId, Pageable pageable);

    Optional<Address> findByIdAndUserId(String id, String userId);
}
