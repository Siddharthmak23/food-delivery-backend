package com.foodieexpress.userservice.controller;

import com.foodieexpress.userservice.dto.LoginRequest;
import com.foodieexpress.userservice.dto.LoginResponse;
import com.foodieexpress.userservice.dto.RegisterRequest;
import com.foodieexpress.userservice.dto.UserResponse;
import com.foodieexpress.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserService.LoginResult result = userService.login(request);
        return new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresAt(),
                UserResponse.from(result.user()));
    }
}
