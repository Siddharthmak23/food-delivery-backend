package com.foodieexpress.restaurantservice.service;

import com.foodieexpress.restaurantservice.dto.MenuItemRequest;
import com.foodieexpress.restaurantservice.dto.MenuQuoteItemRequest;
import com.foodieexpress.restaurantservice.dto.MenuQuoteResponse;
import com.foodieexpress.restaurantservice.dto.PageResponse;
import com.foodieexpress.restaurantservice.dto.RestaurantAddressRequest;
import com.foodieexpress.restaurantservice.dto.RestaurantRequest;
import com.foodieexpress.restaurantservice.exception.InvalidRequestException;
import com.foodieexpress.restaurantservice.exception.ResourceNotFoundException;
import com.foodieexpress.restaurantservice.model.MenuItem;
import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantAddress;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import com.foodieexpress.restaurantservice.repository.MenuItemRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantQueryRepository;
import com.foodieexpress.restaurantservice.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RestaurantService {

    private static final String CURRENCY = "USD";
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantQueryRepository queryRepository;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            RestaurantQueryRepository queryRepository) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.queryRepository = queryRepository;
    }

    public PageResponse<Restaurant> discover(
            String query, String cuisine, int page, int size, Sort.Direction direction) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        String normalizedCuisine = cuisine == null || cuisine.isBlank() ? null : cuisine.trim();
        PageableRequest pageableRequest = pageable(page, size, direction);
        Page<Restaurant> result = queryRepository.discover(
                normalizedQuery, normalizedCuisine, RestaurantStatus.ACTIVE, pageableRequest.pageable());
        return PageResponse.from(result, pageableRequest.sort());
    }

    public Restaurant getVisibleRestaurant(String id) {
        return restaurantRepository.findByIdAndStatus(id.toLowerCase(Locale.ROOT), RestaurantStatus.ACTIVE)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public PageResponse<MenuItem> getMenu(
            String restaurantId, Boolean available, int page, int size, Sort.Direction direction) {
        Restaurant restaurant = getVisibleRestaurant(restaurantId);
        PageableRequest pageableRequest = pageable(page, size, direction);
        Page<MenuItem> result = queryRepository.findMenuItems(
                restaurant.getId(), available, pageableRequest.pageable());
        return PageResponse.from(result, pageableRequest.sort());
    }

    public MenuItem getVisibleMenuItem(String id) {
        MenuItem item = menuItemRepository.findById(id.toLowerCase(Locale.ROOT))
                .orElseThrow(ResourceNotFoundException::new);
        getVisibleRestaurant(item.getRestaurantId());
        return item;
    }

    public MenuQuoteResponse quoteMenu(String restaurantId, List<MenuQuoteItemRequest> requestedItems) {
        Restaurant restaurant = getVisibleRestaurant(restaurantId);
        Set<String> seenIds = new HashSet<>();
        List<MenuQuoteResponse.Item> quotedItems = requestedItems.stream()
                .map(requestedItem -> {
                    String menuItemId = normalizeId(requestedItem.menuItemId());
                    if (!seenIds.add(menuItemId)) {
                        throw new InvalidRequestException("A menu item may appear only once in a quote");
                    }
                    MenuItem menuItem = menuItemRepository
                            .findByIdAndRestaurantId(menuItemId, restaurant.getId())
                            .orElseThrow(ResourceNotFoundException::new);
                    return new MenuQuoteResponse.Item(
                            menuItem.getId(),
                            menuItem.getRestaurantId(),
                            menuItem.getName(),
                            requestedItem.quantity(),
                            menuItem.getPrice().setScale(2).toPlainString(),
                            menuItem.getCurrency(),
                            menuItem.isAvailable());
                })
                .toList();
        return new MenuQuoteResponse(restaurant.getId(), quotedItems);
    }

    public boolean isRestaurantOwner(String restaurantId, String userId) {
        return restaurantRepository.findByIdAndOwnerUserId(
                        normalizeId(restaurantId), normalizeId(userId))
                .isPresent();
    }

    public Restaurant getDeliveryDetails(String restaurantId) {
        return restaurantRepository.findById(normalizeId(restaurantId))
                .orElseThrow(ResourceNotFoundException::new);
    }

    public PageResponse<Restaurant> listForOwner(String ownerUserId, int page, int size, Sort.Direction direction) {
        PageableRequest pageableRequest = pageable(page, size, direction);
        Page<Restaurant> result = restaurantRepository.findAllByOwnerUserId(
                normalizeId(ownerUserId), pageableRequest.pageable());
        return PageResponse.from(result, pageableRequest.sort());
    }

    public PageResponse<Restaurant> listForAdmin(
            String status, int page, int size, Sort.Direction direction) {
        PageableRequest pageableRequest = pageable(page, size, direction);
        Page<Restaurant> result;
        if (status == null || status.isBlank()) {
            result = restaurantRepository.findAll(pageableRequest.pageable());
        } else {
            RestaurantStatus parsedStatus;
            try {
                parsedStatus = RestaurantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("Unsupported restaurant status");
            }
            result = restaurantRepository.findAllByStatus(parsedStatus, pageableRequest.pageable());
        }
        return PageResponse.from(result, pageableRequest.sort());
    }

    public Restaurant create(String ownerUserId, RestaurantRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setOwnerUserId(normalizeId(ownerUserId));
        restaurant.setStatus(RestaurantStatus.PENDING_APPROVAL);
        Instant now = Instant.now();
        restaurant.setCreatedAt(now);
        restaurant.setUpdatedAt(now);
        apply(restaurant, request);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant update(String ownerUserId, String restaurantId, RestaurantRequest request) {
        Restaurant restaurant = ownedRestaurant(ownerUserId, restaurantId);
        apply(restaurant, request);
        restaurant.setUpdatedAt(Instant.now());
        return restaurantRepository.save(restaurant);
    }

    public Restaurant setApproval(String restaurantId, RestaurantStatus status) {
        if (status == RestaurantStatus.PENDING_APPROVAL) {
            throw new InvalidRequestException("A restaurant cannot be returned to pending approval");
        }
        Restaurant restaurant = restaurantRepository.findById(normalizeId(restaurantId))
                .orElseThrow(ResourceNotFoundException::new);
        restaurant.setStatus(status);
        restaurant.setUpdatedAt(Instant.now());
        return restaurantRepository.save(restaurant);
    }

    public MenuItem createMenuItem(String ownerUserId, String restaurantId, MenuItemRequest request) {
        Restaurant restaurant = ownedRestaurant(ownerUserId, restaurantId);
        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(restaurant.getId());
        Instant now = Instant.now();
        menuItem.setCreatedAt(now);
        apply(menuItem, request);
        menuItem.setUpdatedAt(now);
        return menuItemRepository.save(menuItem);
    }

    public MenuItem updateMenuItem(String ownerUserId, String menuItemId, MenuItemRequest request) {
        MenuItem menuItem = ownedMenuItem(ownerUserId, menuItemId);
        apply(menuItem, request);
        menuItem.setUpdatedAt(Instant.now());
        return menuItemRepository.save(menuItem);
    }

    public MenuItem setMenuItemAvailability(String ownerUserId, String menuItemId, boolean available) {
        MenuItem menuItem = ownedMenuItem(ownerUserId, menuItemId);
        menuItem.setAvailable(available);
        menuItem.setUpdatedAt(Instant.now());
        return menuItemRepository.save(menuItem);
    }

    private Restaurant ownedRestaurant(String ownerUserId, String restaurantId) {
        return restaurantRepository.findByIdAndOwnerUserId(
                        normalizeId(restaurantId), normalizeId(ownerUserId))
                .orElseThrow(ResourceNotFoundException::new);
    }

    private MenuItem ownedMenuItem(String ownerUserId, String menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId.toLowerCase(Locale.ROOT))
                .orElseThrow(ResourceNotFoundException::new);
        ownedRestaurant(ownerUserId, menuItem.getRestaurantId());
        return menuItem;
    }

    private void apply(Restaurant restaurant, RestaurantRequest request) {
        restaurant.setName(request.name().trim());
        restaurant.setDescription(cleanOptional(request.description()));
        restaurant.setCuisines(request.cuisines().stream().map(String::trim).toList());
        RestaurantAddressRequest address = request.address();
        restaurant.setAddress(new RestaurantAddress(
                address.line1().trim(),
                cleanOptional(address.line2()),
                address.city().trim(),
                address.region().trim(),
                address.postalCode().trim(),
                address.countryCode()));
    }

    private void apply(MenuItem menuItem, MenuItemRequest request) {
        menuItem.setName(request.name().trim());
        menuItem.setDescription(cleanOptional(request.description()));
        menuItem.setCategory(request.category().trim());
        menuItem.setPrice(new BigDecimal(request.price()).setScale(2));
        menuItem.setCurrency(CURRENCY);
        menuItem.setAvailable(request.available());
    }

    private PageableRequest pageable(int page, int size, Sort.Direction direction) {
        String sort = "name," + direction.name().toLowerCase(Locale.ROOT);
        Sort order = Sort.by(direction, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        return new PageableRequest(PageRequest.of(page, size, order), sort);
    }

    private String normalizeId(String id) {
        if (id == null || !id.matches("^[a-fA-F0-9]{24}$")) {
            throw new InvalidRequestException("Identifier must be a 24-character hexadecimal ObjectId");
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PageableRequest(org.springframework.data.domain.Pageable pageable, String sort) {
    }
}
