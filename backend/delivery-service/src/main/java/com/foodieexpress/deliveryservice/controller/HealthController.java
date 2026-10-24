package com.foodieexpress.deliveryservice.controller;

import com.foodieexpress.deliveryservice.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("delivery-service", "UP", "NOT_CHECKED");
    }
}
