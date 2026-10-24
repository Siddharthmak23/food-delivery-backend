package com.foodieexpress.orderservice.controller;

import com.foodieexpress.orderservice.dto.CancelOrderRequest;
import com.foodieexpress.orderservice.dto.CreateOrderRequest;
import com.foodieexpress.orderservice.dto.OrderResponse;
import com.foodieexpress.orderservice.dto.PageResponse;
import com.foodieexpress.orderservice.dto.RestaurantOrderStatusRequest;
import com.foodieexpress.orderservice.model.Order;
import com.foodieexpress.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[\\x21-\\x7E]{1,128}$") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = orderService.create(jwt.getSubject(), idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(OrderResponse.from(result.order()));
    }

    @GetMapping("/my-orders")
    public PageResponse<OrderResponse> listMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listCustomerOrders(jwt.getSubject(), page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        return OrderResponse.from(orderService.getCustomerOrder(jwt.getSubject(), id));
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request == null ? null : request.reason();
        Order order = orderService.cancelCustomerOrder(jwt.getSubject(), id, reason);
        return OrderResponse.from(order);
    }

    @GetMapping("/restaurant/{restaurantId}")
    public PageResponse<OrderResponse> listRestaurantOrders(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String restaurantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listRestaurantOrders(jwt.getSubject(), restaurantId, page, size);
    }

    @PatchMapping("/{id}/restaurant-status")
    public ResponseEntity<OrderResponse> updateRestaurantStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody RestaurantOrderStatusRequest request) {
        Order order = orderService.updateRestaurantOrder(
                jwt.getSubject(), id, request.status(), request.reason());
        return ResponseEntity.status(order.getDeliveryProvisioningStatus()
                        == com.foodieexpress.orderservice.model.DeliveryProvisioningStatus.RETRY_REQUIRED
                        || order.getDeliveryProvisioningStatus()
                                == com.foodieexpress.orderservice.model.DeliveryProvisioningStatus.PENDING
                        ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(OrderResponse.from(order));
    }

    @PostMapping("/{id}/delivery-provisioning/retry")
    public ResponseEntity<OrderResponse> retryDeliveryProvisioning(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        Order order = orderService.retryDeliveryProvisioning(id);
        return ResponseEntity.status(order.getDeliveryProvisioningStatus()
                        == com.foodieexpress.orderservice.model.DeliveryProvisioningStatus.COMPLETE
                        ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(OrderResponse.from(order));
    }

}
