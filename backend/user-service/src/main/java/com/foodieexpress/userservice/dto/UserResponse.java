package com.foodieexpress.userservice.dto;

import com.foodieexpress.userservice.model.User;
import com.foodieexpress.userservice.model.UserRole;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        String id,
        String email,
        String displayName,
        String phone,
        List<UserRole> roles,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRoles().stream().sorted().toList(),
                user.getCreatedAt());
    }
}
