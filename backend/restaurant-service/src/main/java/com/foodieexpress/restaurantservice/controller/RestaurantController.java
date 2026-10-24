package com.foodieexpress.restaurantservice.controller;

import com.foodieexpress.restaurantservice.dto.MenuAvailabilityRequest;
import com.foodieexpress.restaurantservice.dto.MenuItemRequest;
import com.foodieexpress.restaurantservice.dto.MenuItemResponse;
import com.foodieexpress.restaurantservice.dto.MenuQuoteRequest;
import com.foodieexpress.restaurantservice.dto.MenuQuoteResponse;
import com.foodieexpress.restaurantservice.dto.PageResponse;
import com.foodieexpress.restaurantservice.dto.RestaurantApprovalRequest;
import com.foodieexpress.restaurantservice.dto.RestaurantRequest;
import com.foodieexpress.restaurantservice.dto.RestaurantOwnerVerificationResponse;
import com.foodieexpress.restaurantservice.dto.RestaurantDeliveryDetailsResponse;
import com.foodieexpress.restaurantservice.dto.RestaurantResponse;
import com.foodieexpress.restaurantservice.exception.InvalidRequestException;
import com.foodieexpress.restaurantservice.service.RestaurantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/api/restaurants")
    public PageResponse<RestaurantResponse> discover(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 60) String cuisine,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return restaurantService.discover(q, cuisine, page, size, direction(sort))
                .map(RestaurantResponse::from);
    }

    @GetMapping("/api/restaurants/my-restaurants")
    public PageResponse<RestaurantResponse> getMyRestaurants(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return restaurantService.listForOwner(jwt.getSubject(), page, size, direction(sort))
                .map(RestaurantResponse::from);
    }

    @GetMapping("/api/restaurants/{id}")
    public RestaurantResponse getRestaurant(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        return RestaurantResponse.from(restaurantService.getVisibleRestaurant(id));
    }

    @GetMapping("/api/restaurants/{id}/menu")
    public PageResponse<MenuItemResponse> getMenu(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return restaurantService.getMenu(id, available, page, size, direction(sort))
                .map(MenuItemResponse::from);
    }

    @GetMapping("/api/menu-items/{id}")
    public MenuItemResponse getMenuItem(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        return MenuItemResponse.from(restaurantService.getVisibleMenuItem(id));
    }

    @PostMapping("/internal/restaurants/{id}/menu-quote")
    public MenuQuoteResponse quoteMenu(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody MenuQuoteRequest request) {
        return restaurantService.quoteMenu(id, request.items());
    }

    @GetMapping("/internal/restaurants/{restaurantId}/owners/{userId}")
    public RestaurantOwnerVerificationResponse verifyRestaurantOwner(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String restaurantId,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String userId) {
        if (!restaurantService.isRestaurantOwner(restaurantId, userId)) {
            throw new com.foodieexpress.restaurantservice.exception.ResourceNotFoundException();
        }
        return new RestaurantOwnerVerificationResponse(
                restaurantId.toLowerCase(java.util.Locale.ROOT),
                userId.toLowerCase(java.util.Locale.ROOT),
                true);
    }

    @GetMapping("/internal/restaurants/{id}/delivery-details")
    public RestaurantDeliveryDetailsResponse getDeliveryDetails(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        return RestaurantDeliveryDetailsResponse.from(restaurantService.getDeliveryDetails(id));
    }

    @PostMapping("/api/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse createRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RestaurantRequest request) {
        return RestaurantResponse.from(restaurantService.create(jwt.getSubject(), request));
    }

    @PutMapping("/api/restaurants/{id}")
    public RestaurantResponse updateRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody RestaurantRequest request) {
        return RestaurantResponse.from(restaurantService.update(jwt.getSubject(), id, request));
    }

    @PostMapping("/api/restaurants/{id}/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createMenuItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody MenuItemRequest request) {
        return MenuItemResponse.from(restaurantService.createMenuItem(jwt.getSubject(), id, request));
    }

    @PutMapping("/api/menu-items/{id}")
    public MenuItemResponse updateMenuItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody MenuItemRequest request) {
        return MenuItemResponse.from(restaurantService.updateMenuItem(jwt.getSubject(), id, request));
    }

    @PatchMapping("/api/menu-items/{id}/availability")
    public MenuItemResponse setAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody MenuAvailabilityRequest request) {
        return MenuItemResponse.from(
                restaurantService.setMenuItemAvailability(jwt.getSubject(), id, request.available()));
    }

    @PatchMapping("/api/admin/restaurants/{id}/approval")
    public RestaurantResponse setApproval(
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody RestaurantApprovalRequest request) {
        return RestaurantResponse.from(restaurantService.setApproval(id, request.status()));
    }

    @GetMapping("/api/admin/restaurants")
    public PageResponse<RestaurantResponse> listForAdmin(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 32) String status,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return restaurantService.listForAdmin(status, page, size, direction(sort))
                .map(RestaurantResponse::from);
    }

    private Sort.Direction direction(String sort) {
        return switch (sort) {
            case "name,asc" -> Sort.Direction.ASC;
            case "name,desc" -> Sort.Direction.DESC;
            default -> throw new InvalidRequestException("Unsupported restaurant sort order");
        };
    }
}
