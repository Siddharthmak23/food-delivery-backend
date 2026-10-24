package com.foodieexpress.deliveryservice.controller;

import com.foodieexpress.deliveryservice.dto.AssignDeliveryRequest;
import com.foodieexpress.deliveryservice.dto.CancelDeliveryRequest;
import com.foodieexpress.deliveryservice.dto.CreateDeliveryRequest;
import com.foodieexpress.deliveryservice.dto.DeliveryResponse;
import com.foodieexpress.deliveryservice.dto.DeliveryTimelineResponse;
import com.foodieexpress.deliveryservice.dto.PageResponse;
import com.foodieexpress.deliveryservice.dto.UpdateDeliveryStatusRequest;
import com.foodieexpress.deliveryservice.service.DeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
@RequestMapping("/api/deliveries")
@Validated
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "^[a-fA-F0-9]{24}$") String idempotencyKey,
            @Valid @RequestBody CreateDeliveryRequest request) {
        var result = deliveryService.create(idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(DeliveryResponse.from(result.delivery()));
    }

    @GetMapping("/my-assignments")
    public PageResponse<DeliveryResponse> getMyAssignments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        requireRole(jwt, "DELIVERY_OPERATOR", "ADMIN");
        return deliveryService.listForOperator(jwt.getSubject(), page, size, org.springframework.data.domain.Sort.Direction.DESC)
                .map(DeliveryResponse::from);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @AuthenticationPrincipal Jwt jwt) {
        return DeliveryResponse.from(deliveryService.getForUser(id, jwt));
    }

    @GetMapping("/order/{orderId}")
    public DeliveryResponse getByOrder(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return DeliveryResponse.from(deliveryService.getByOrderForUser(orderId, jwt));
    }

    @PatchMapping("/{id}/assign")
    public DeliveryResponse assign(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssignDeliveryRequest request) {
        requireRole(jwt, "DELIVERY_OPERATOR", "ADMIN");
        boolean assignToCaller = jwt.getClaimAsStringList("roles") != null
                && jwt.getClaimAsStringList("roles").contains("DELIVERY_OPERATOR");
        return DeliveryResponse.from(deliveryService.assign(id, jwt.getSubject(), request, assignToCaller));
    }

    @PatchMapping("/{id}/status")
    public DeliveryResponse updateStatus(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        requireRole(jwt, "DELIVERY_OPERATOR", "ADMIN");
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null
                && jwt.getClaimAsStringList("roles").contains("ADMIN");
        return DeliveryResponse.from(deliveryService.updateStatus(id, jwt.getSubject(), request.status(), isAdmin));
    }

    @PatchMapping("/{id}/cancel")
    public DeliveryResponse cancel(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CancelDeliveryRequest request) {
        boolean internal = "order-service".equals(jwt.getSubject())
                && jwt.getClaimAsStringList("scopes") != null
                && jwt.getClaimAsStringList("scopes").contains("delivery:cancel");
        if (!internal) {
            requireRole(jwt, "ADMIN");
        }
        boolean isAdmin = jwt.getClaimAsStringList("roles") != null
                && jwt.getClaimAsStringList("roles").contains("ADMIN");
        return DeliveryResponse.from(deliveryService.cancel(id, jwt.getSubject(), request.reason(), isAdmin));
    }

    @GetMapping("/{id}/timeline")
    public DeliveryTimelineResponse timeline(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @AuthenticationPrincipal Jwt jwt) {
        var delivery = deliveryService.getForUser(id, jwt);
        return new DeliveryTimelineResponse(
                delivery.getId(),
                delivery.getEvents().stream().map(event -> new DeliveryResponse.Event(
                        event.eventId(), event.type(), event.occurredAt(), event.actorId(),
                        event.source(), event.callbackPending())).toList());
    }

    private void requireRole(Jwt jwt, String... allowedRoles) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles == null || java.util.Arrays.stream(allowedRoles).noneMatch(roles::contains)) {
            throw new AccessDeniedException("Access is denied");
        }
    }
}
