package com.foodieexpress.orderservice.controller;

import com.foodieexpress.orderservice.dto.AdminCancelOrderRequest;
import com.foodieexpress.orderservice.dto.AdminOrderResponse;
import com.foodieexpress.orderservice.dto.OrderResponse;
import com.foodieexpress.orderservice.dto.PageResponse;
import com.foodieexpress.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<AdminOrderResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listAllOrders(page, size);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody AdminCancelOrderRequest request) {
        return OrderResponse.from(orderService.cancelOrderAsAdmin(id, request.reason()));
    }
}
