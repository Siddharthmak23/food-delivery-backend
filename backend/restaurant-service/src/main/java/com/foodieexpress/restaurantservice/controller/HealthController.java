package com.foodieexpress.restaurantservice.controller;

import com.foodieexpress.restaurantservice.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("restaurant-service", "UP", "NOT_CHECKED");
    }
}
