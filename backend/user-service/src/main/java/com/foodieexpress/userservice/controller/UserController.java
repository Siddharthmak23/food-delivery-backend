package com.foodieexpress.userservice.controller;

import com.foodieexpress.userservice.dto.UpdateProfileRequest;
import com.foodieexpress.userservice.dto.UserResponse;
import com.foodieexpress.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getActiveUser(jwt.getSubject()));
    }

    @PutMapping
    public UserResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.updateProfile(jwt.getSubject(), request));
    }
}
