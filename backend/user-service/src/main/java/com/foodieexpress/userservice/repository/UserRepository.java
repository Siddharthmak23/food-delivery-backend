package com.foodieexpress.userservice.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.foodieexpress.userservice.model.User;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);
}
