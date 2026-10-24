package com.foodieexpress.orderservice.controller;

import com.foodieexpress.orderservice.dto.DeliveryStatusCallbackRequest;
import com.foodieexpress.orderservice.dto.OrderResponse;
import com.foodieexpress.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@Validated
public class InternalDeliveryStatusController {

    private final OrderService orderService;

    public InternalDeliveryStatusController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{id}/delivery-status")
    public OrderResponse updateDeliveryStatus(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody DeliveryStatusCallbackRequest request) {
        return OrderResponse.from(orderService.applyDeliveryStatus(id, request));
    }
}
