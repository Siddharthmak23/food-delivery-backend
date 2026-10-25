package com.foodieexpress.restaurantservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.foodieexpress.restaurantservice.exception.GlobalExceptionHandler;
import com.foodieexpress.restaurantservice.dto.RestaurantAddressRequest;
import com.foodieexpress.restaurantservice.dto.RestaurantRequest;
import com.foodieexpress.restaurantservice.exception.RestaurantConflictException;
import com.foodieexpress.restaurantservice.repository.MenuItemRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantQueryRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RestaurantServiceOwnerLimitTests {

    @Test
    void createRejectsAdditionalRestaurantForSameOwner() {
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantQueryRepository queryRepository = mock(RestaurantQueryRepository.class);
        RestaurantService service = new RestaurantService(restaurantRepository, menuItemRepository, queryRepository);

        when(restaurantRepository.countByOwnerUserId("672000000000000000000001")).thenReturn(1L);

        RestaurantRequest request = new RestaurantRequest(
                "Fresh Bowl",
                "Neighborhood kitchen",
                List.of("Indian"),
                new RestaurantAddressRequest(
                        "12 Market Road",
                        "",
                        "Mumbai",
                        "Maharashtra",
                        "400001",
                        "IN"));

        RestaurantConflictException exception = assertThrows(
                RestaurantConflictException.class,
                () -> service.create("672000000000000000000001", request));

        assertEquals(
                "This owner already has a restaurant. Each owner can create at most one kitchen.",
                exception.getMessage());
        verify(restaurantRepository, never()).save(any());

        var response = new GlobalExceptionHandler().handleRestaurantConflict(exception);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(exception.getMessage(), response.getBody().message());
    }
}
