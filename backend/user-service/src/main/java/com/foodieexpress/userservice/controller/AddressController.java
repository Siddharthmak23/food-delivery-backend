package com.foodieexpress.userservice.controller;

import com.foodieexpress.userservice.dto.AddressRequest;
import com.foodieexpress.userservice.dto.AddressResponse;
import com.foodieexpress.userservice.dto.PageResponse;
import com.foodieexpress.userservice.exception.InvalidRequestException;
import com.foodieexpress.userservice.service.AddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/addresses")
@Validated
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public PageResponse<AddressResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Sort.Direction direction = switch (sort) {
            case "createdAt,desc" -> Sort.Direction.DESC;
            case "createdAt,asc" -> Sort.Direction.ASC;
            default -> throw new InvalidRequestException("Unsupported address sort order");
        };
        return addressService.list(jwt.getSubject(), page, size, direction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(addressService.create(jwt.getSubject(), request));
    }

    @PutMapping("/{id}")
    public AddressResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id,
            @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(addressService.update(jwt.getSubject(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[a-fA-F0-9]{24}$") String id) {
        addressService.delete(jwt.getSubject(), id);
    }
}
