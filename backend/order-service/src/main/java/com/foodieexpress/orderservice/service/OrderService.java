package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.CreateOrderItemRequest;
import com.foodieexpress.orderservice.dto.CreateOrderRequest;
import com.foodieexpress.orderservice.dto.DeliveryCreateRequest;
import com.foodieexpress.orderservice.dto.DeliveryCreateResponse;
import com.foodieexpress.orderservice.dto.DeliveryStatusCallbackRequest;
import com.foodieexpress.orderservice.dto.PageResponse;
import com.foodieexpress.orderservice.dto.RestaurantDeliveryDetailsResponse;
import com.foodieexpress.orderservice.dto.RestaurantQuoteRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteResponse;
import com.foodieexpress.orderservice.dto.UserAddressResponse;
import com.foodieexpress.orderservice.exception.InvalidOrderException;
import com.foodieexpress.orderservice.exception.OrderConflictException;
import com.foodieexpress.orderservice.exception.ResourceNotFoundException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.model.CancellationStatus;
import com.foodieexpress.orderservice.model.DeliveryProvisioningStatus;
import com.foodieexpress.orderservice.model.Order;
import com.foodieexpress.orderservice.model.OrderDeliveryEvent;
import com.foodieexpress.orderservice.model.OrderAddressSnapshot;
import com.foodieexpress.orderservice.model.OrderIdempotency;
import com.foodieexpress.orderservice.model.OrderItemSnapshot;
import com.foodieexpress.orderservice.model.OrderStatus;
import com.foodieexpress.orderservice.model.PaymentStatus;
import com.foodieexpress.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

@Service
public class OrderService {

    private static final BigDecimal DELIVERY_FEE = new BigDecimal("3.99");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final Pattern PRICE_FORMAT = Pattern.compile("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?$");
    private static final Pattern OBJECT_ID = Pattern.compile("^[a-fA-F0-9]{24}$");
    private static final int MAX_MONEY_PRECISION = 34;
    private static final int MAX_DELIVERY_EVENTS = 3;

    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final AddressLookupClient addressLookupClient;
    private final RestaurantQuoteClient restaurantQuoteClient;
    private final RestaurantOwnerClient restaurantOwnerClient;
    private final DeliveryProvisioningClient deliveryProvisioningClient;

    public OrderService(
            OrderRepository orderRepository,
            MongoTemplate mongoTemplate,
            AddressLookupClient addressLookupClient,
            RestaurantQuoteClient restaurantQuoteClient,
            RestaurantOwnerClient restaurantOwnerClient,
            DeliveryProvisioningClient deliveryProvisioningClient) {
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
        this.addressLookupClient = addressLookupClient;
        this.restaurantQuoteClient = restaurantQuoteClient;
        this.restaurantOwnerClient = restaurantOwnerClient;
        this.deliveryProvisioningClient = deliveryProvisioningClient;
    }

    public CreateResult create(String customerId, String idempotencyKey, CreateOrderRequest request) {
        String normalizedCustomerId = normalizeObjectId(customerId, "Authenticated user identity is invalid");
        String restaurantId = normalizeObjectId(request.getRestaurantId(), "Restaurant ID is invalid");
        String addressId = normalizeObjectId(request.getAddressId(), "Address ID is invalid");
        List<CreateOrderItemRequest> requestedItems = normalizeItems(request.getItems());
        String fingerprint = fingerprint(restaurantId, addressId, requestedItems);

        var existing = orderRepository.findByCustomerIdAndIdempotencyKey(normalizedCustomerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        UserAddressResponse addressResponse =
                addressLookupClient.getOwnedAddress(normalizedCustomerId, addressId);
        RestaurantQuoteResponse quote = restaurantQuoteClient.quote(
                restaurantId,
                new RestaurantQuoteRequest(requestedItems.stream()
                        .map(item -> new RestaurantQuoteRequest.Item(
                                item.getMenuItemId().toLowerCase(Locale.ROOT), item.getQuantity()))
                        .toList()));

        List<OrderItemSnapshot> snapshots = validateAndSnapshot(restaurantId, requestedItems, quote);
        BigDecimal subtotal = snapshots.stream()
                .map(OrderItemSnapshot::lineTotal)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal total = subtotal.add(DELIVERY_FEE).subtract(ZERO_MONEY);
        if (subtotal.precision() > MAX_MONEY_PRECISION || total.precision() > MAX_MONEY_PRECISION) {
            throw new InvalidOrderException("Order amount exceeds the supported limit");
        }
        Instant now = Instant.now();

        Order order = new Order();
        order.setCustomerId(normalizedCustomerId);
        order.setRestaurantId(restaurantId);
        order.setItems(snapshots);
        order.setDeliveryAddress(addressSnapshot(addressResponse.address()));
        order.setCurrency("USD");
        order.setSubtotal(subtotal);
        order.setDeliveryFee(DELIVERY_FEE);
        order.setDiscount(ZERO_MONEY);
        order.setTotal(total);
        order.setPaymentStatus(PaymentStatus.SIMULATED_UNPAID);
        order.setStatus(OrderStatus.PLACED);
        order.setDeliveryProvisioningStatus(DeliveryProvisioningStatus.NOT_REQUESTED);
        order.setCancellationStatus(CancellationStatus.NONE);
        order.setIdempotency(new OrderIdempotency(idempotencyKey, fingerprint));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setStatusUpdatedAt(now);
        try {
            return new CreateResult(orderRepository.save(order), false);
        } catch (DuplicateKeyException exception) {
            return orderRepository.findByCustomerIdAndIdempotencyKey(normalizedCustomerId, idempotencyKey)
                    .map(saved -> replay(saved, fingerprint))
                    .orElseThrow(() -> new OrderConflictException("The idempotency key is already in use"));
        }
    }

    public PageResponse<com.foodieexpress.orderservice.dto.OrderResponse> listCustomerOrders(
            String customerId, int page, int size) {
        String normalizedCustomerId = normalizeObjectId(customerId, "Authenticated user identity is invalid");
        var orders = orderRepository.findAllByCustomerId(
                normalizedCustomerId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.from(
                orders, "createdAt,desc", com.foodieexpress.orderservice.dto.OrderResponse::from);
    }

    public Order getCustomerOrder(String customerId, String orderId) {
        String normalizedCustomerId = normalizeObjectId(customerId, "Authenticated user identity is invalid");
        String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
        return orderRepository.findByIdAndCustomerId(normalizedOrderId, normalizedCustomerId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public Order cancelCustomerOrder(String customerId, String orderId, String reason) {
        String normalizedCustomerId = normalizeObjectId(customerId, "Authenticated user identity is invalid");
        String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("_id").is(normalizedOrderId)
                .and("customerId").is(normalizedCustomerId)
                .and("status").is(OrderStatus.PLACED));
        Update update = new Update()
                .set("status", OrderStatus.CANCELLED)
                .set("cancellationStatus", CancellationStatus.COMPLETE)
                .set("cancellationReason", reason == null || reason.isBlank() ? null : reason.trim())
                .set("updatedAt", now)
                .set("statusUpdatedAt", now);
        Order cancelled = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Order.class);
        if (cancelled != null) {
            return cancelled;
        }
        Order existing = orderRepository.findByIdAndCustomerId(normalizedOrderId, normalizedCustomerId)
                .orElseThrow(ResourceNotFoundException::new);
        if (existing.getStatus() == OrderStatus.CANCELLED) {
            return existing;
        }
        throw new OrderConflictException("Only a PLACED order can be cancelled by its customer");
    }

        public PageResponse<com.foodieexpress.orderservice.dto.OrderResponse> listRestaurantOrders(
                String ownerId, String restaurantId, int page, int size) {
            String normalizedOwnerId = normalizeObjectId(ownerId, "Authenticated user identity is invalid");
            String normalizedRestaurantId = normalizeObjectId(restaurantId, "Restaurant ID is invalid");
            requireRestaurantOwner(normalizedRestaurantId, normalizedOwnerId);
            var orders = orderRepository.findAllByRestaurantId(
                    normalizedRestaurantId,
                    PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
            return PageResponse.from(orders, "createdAt,asc",
                    com.foodieexpress.orderservice.dto.OrderResponse::from);
        }

        public Order updateRestaurantOrder(
                String ownerId, String orderId, String requestedStatus, String reason) {
            String normalizedOwnerId = normalizeObjectId(ownerId, "Authenticated user identity is invalid");
            String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
            Order order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
            requireRestaurantOwner(order.getRestaurantId(), normalizedOwnerId);
            OrderStatus target = OrderStatus.valueOf(requestedStatus);

            if (target == OrderStatus.CONFIRMED) {
                if (order.getStatus() == OrderStatus.PLACED) {
                    Instant now = Instant.now();
                    order = mongoTemplate.findAndModify(
                            Query.query(Criteria.where("_id").is(normalizedOrderId)
                                    .and("status").is(OrderStatus.PLACED)),
                            new Update().set("status", OrderStatus.CONFIRMED)
                                    .set("deliveryProvisioningStatus", DeliveryProvisioningStatus.PENDING)
                                    .set("updatedAt", now).set("statusUpdatedAt", now),
                            FindAndModifyOptions.options().returnNew(true),
                            Order.class);
                    if (order == null) {
                        order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
                    }
                }
                if (order.getStatus() != OrderStatus.CONFIRMED) {
                    throw new OrderConflictException("Only a PLACED order can be confirmed");
                }
                if (order.getDeliveryId() != null
                        && order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.COMPLETE) {
                    return order;
                }
                if (order.getDeliveryId() == null
                        && order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.COMPLETE) {
                    throw new OrderConflictException("Delivery association is incomplete");
                }
                if (order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.NOT_REQUESTED) {
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(normalizedOrderId)
                                    .and("status").is(OrderStatus.CONFIRMED)
                                    .and("deliveryId").is(null)
                                    .and("deliveryProvisioningStatus").is(DeliveryProvisioningStatus.NOT_REQUESTED)),
                            new Update().set("deliveryProvisioningStatus", DeliveryProvisioningStatus.PENDING)
                                    .set("updatedAt", Instant.now()),
                            Order.class);
                    order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
                }
                return provisionDelivery(order);
            }

            if (target == OrderStatus.REJECTED) {
                if (reason == null || reason.isBlank()) {
                    throw new InvalidOrderException("A rejection reason is required");
                }
                return transitionRestaurantOrder(normalizedOrderId, OrderStatus.PLACED, target, reason.trim());
            }

            if (target == OrderStatus.PREPARING) {
                return transitionRestaurantOrder(normalizedOrderId, OrderStatus.CONFIRMED, target, null);
            }
            if (target == OrderStatus.READY_FOR_PICKUP) {
                Order current = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
                if (current.getDeliveryProvisioningStatus() != DeliveryProvisioningStatus.COMPLETE
                        || current.getDeliveryId() == null) {
                    throw new OrderConflictException("Delivery must be provisioned before the order is ready");
                }
                return transitionRestaurantOrder(normalizedOrderId, OrderStatus.PREPARING, target, null);
            }
            throw new OrderConflictException("Restaurant owners cannot set the requested order status");
        }

        public Order retryDeliveryProvisioning(String orderId) {
            String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
            Order order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
            if ((order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PREPARING)
                    || order.getDeliveryId() != null
                    || (order.getDeliveryProvisioningStatus() != DeliveryProvisioningStatus.RETRY_REQUIRED
                            && order.getDeliveryProvisioningStatus() != DeliveryProvisioningStatus.PENDING)) {
                throw new OrderConflictException("Delivery provisioning is not pending for this order");
            }
            return provisionDelivery(order);
        }

        public Order cancelOrderAsAdmin(String orderId, String reason) {
            String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
            Order order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return order;
            }
            if (order.getStatus() != OrderStatus.CONFIRMED
                    && order.getStatus() != OrderStatus.PREPARING
                    && order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
                throw new OrderConflictException("Only accepted orders can be cancelled by an administrator");
            }
            if (order.getDeliveryId() == null
                    && order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.NOT_REQUESTED) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(normalizedOrderId)
                                .and("deliveryId").is(null)
                                .and("deliveryProvisioningStatus").is(DeliveryProvisioningStatus.NOT_REQUESTED)),
                        new Update().set("deliveryProvisioningStatus", DeliveryProvisioningStatus.PENDING)
                                .set("updatedAt", Instant.now()),
                        Order.class);
                order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
            }
            if (order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.PENDING
                    || order.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.RETRY_REQUIRED) {
                order = provisionDelivery(order);
            }
            if (order.getDeliveryId() == null) {
                throw new OrderConflictException("Delivery provisioning must complete before cancellation");
            }
            if (order.getCancellationStatus() == CancellationStatus.NONE) {
                Order pending = mongoTemplate.findAndModify(
                        Query.query(Criteria.where("_id").is(normalizedOrderId)
                                .and("status").in(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
                                .and("cancellationStatus").is(CancellationStatus.NONE)),
                        new Update().set("cancellationStatus", CancellationStatus.PENDING)
                                .set("cancellationReason", reason.trim())
                                .set("updatedAt", Instant.now()),
                        FindAndModifyOptions.options().returnNew(true),
                        Order.class);
                if (pending == null) {
                    order = orderRepository.findById(normalizedOrderId).orElseThrow(ResourceNotFoundException::new);
                    if (order.getStatus() == OrderStatus.CANCELLED) {
                        return order;
                    }
                } else {
                    order = pending;
                }
            }
            if (order.getCancellationStatus() == CancellationStatus.RETRY_REQUIRED) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(normalizedOrderId)
                                .and("cancellationStatus").is(CancellationStatus.RETRY_REQUIRED)),
                        new Update().set("cancellationStatus", CancellationStatus.PENDING)
                                .set("updatedAt", Instant.now()),
                        Order.class);
            }
            try {
                deliveryProvisioningClient.cancel(
                        order.getDeliveryId(),
                        order.getCancellationReason() == null ? reason.trim() : order.getCancellationReason());
                Order cancelled = orderRepository.findById(normalizedOrderId)
                        .orElseThrow(ResourceNotFoundException::new);
                if (cancelled.getStatus() != OrderStatus.CANCELLED) {
                    throw new DependencyUnavailableException();
                }
                return cancelled;
            } catch (DependencyUnavailableException | UpstreamResponseException exception) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(normalizedOrderId)
                                .and("status").in(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)
                                .and("cancellationStatus").is(CancellationStatus.PENDING)),
                        new Update().set("cancellationStatus", CancellationStatus.RETRY_REQUIRED)
                                .set("updatedAt", Instant.now()),
                        Order.class);
                throw exception;
            }
        }

        private void requireRestaurantOwner(String restaurantId, String ownerId) {
            if (!restaurantOwnerClient.isOwner(restaurantId, ownerId)) {
                throw new AccessDeniedException("Access is denied");
            }
        }

        private Order transitionRestaurantOrder(
                String orderId, OrderStatus expected, OrderStatus target, String reason) {
            Order current = orderRepository.findById(orderId).orElseThrow(ResourceNotFoundException::new);
            if (current.getStatus() != expected
                    || current.getCancellationStatus() == CancellationStatus.PENDING
                    || current.getCancellationStatus() == CancellationStatus.RETRY_REQUIRED) {
                if (current.getStatus() == target
                        && current.getCancellationStatus() != CancellationStatus.PENDING
                        && current.getCancellationStatus() != CancellationStatus.RETRY_REQUIRED) {
                    return current;
                }
                throw new OrderConflictException("Order status transition is not allowed");
            }
            Instant now = Instant.now();
            Update update = new Update()
                    .set("status", target)
                    .set("updatedAt", now)
                    .set("statusUpdatedAt", now);
            if (reason != null) {
                update.set("rejectionReason", reason);
            }
            Order changed = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(orderId)
                            .and("status").is(expected)
                            .and("cancellationStatus").nin(CancellationStatus.PENDING, CancellationStatus.RETRY_REQUIRED)),
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    Order.class);
            if (changed != null) {
                return changed;
            }
            Order latest = orderRepository.findById(orderId).orElseThrow(ResourceNotFoundException::new);
            if (latest.getStatus() == target) {
                return latest;
            }
            throw new OrderConflictException("Order status changed; reload and retry");
        }

        private Order provisionDelivery(Order order) {
            if (order.getDeliveryId() != null) {
                return order;
            }
            if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PREPARING) {
                throw new OrderConflictException("Order is not eligible for delivery provisioning");
            }
            try {
                RestaurantDeliveryDetailsResponse restaurant =
                        restaurantOwnerClient.getDeliveryDetails(order.getRestaurantId());
                DeliveryCreateRequest request = deliveryRequest(order, restaurant);
                DeliveryCreateResponse response = deliveryProvisioningClient.create(order.getId(), request);
                validateDeliveryResponse(order, response);
                Instant now = Instant.now();
                Order changed = mongoTemplate.findAndModify(
                        Query.query(Criteria.where("_id").is(order.getId())
                                .and("status").in(OrderStatus.CONFIRMED, OrderStatus.PREPARING)
                                .and("deliveryId").is(null)
                                .and("deliveryProvisioningStatus")
                                        .in(DeliveryProvisioningStatus.PENDING, DeliveryProvisioningStatus.RETRY_REQUIRED)),
                        new Update().set("deliveryId", response.id().toLowerCase(Locale.ROOT))
                                .set("deliveryProvisioningStatus", DeliveryProvisioningStatus.COMPLETE)
                                .set("updatedAt", now),
                        FindAndModifyOptions.options().returnNew(true),
                        Order.class);
                if (changed != null) {
                    return changed;
                }
                Order latest = orderRepository.findById(order.getId()).orElseThrow(ResourceNotFoundException::new);
                if (response.id().equalsIgnoreCase(latest.getDeliveryId())
                        && latest.getDeliveryProvisioningStatus() == DeliveryProvisioningStatus.COMPLETE) {
                    return latest;
                }
                throw new OrderConflictException("Order changed while delivery was being provisioned");
            } catch (DependencyUnavailableException | UpstreamResponseException | ResourceNotFoundException exception) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(order.getId())
                                .and("status").in(OrderStatus.CONFIRMED, OrderStatus.PREPARING)
                                .and("deliveryId").is(null)
                                .and("deliveryProvisioningStatus").is(DeliveryProvisioningStatus.PENDING)),
                        new Update().set("deliveryProvisioningStatus", DeliveryProvisioningStatus.RETRY_REQUIRED)
                                .set("updatedAt", Instant.now()),
                        Order.class);
                return orderRepository.findById(order.getId()).orElseThrow(ResourceNotFoundException::new);
            }
        }

        private DeliveryCreateRequest deliveryRequest(
                Order order, RestaurantDeliveryDetailsResponse restaurant) {
            var pickup = restaurant.address();
            var dropoff = order.getDeliveryAddress();
            if (pickup == null || dropoff == null) {
                throw new UpstreamResponseException();
            }
            return new DeliveryCreateRequest(
                    order.getId(),
                    order.getRestaurantId(),
                    order.getCustomerId(),
                    new DeliveryCreateRequest.Address(
                            restaurant.name(), pickup.line1(), pickup.line2(), pickup.city(), pickup.region(),
                            pickup.postalCode(), pickup.countryCode(), null),
                    new DeliveryCreateRequest.Address(
                            dropoff.recipientName(), dropoff.line1(), dropoff.line2(), dropoff.city(),
                            dropoff.region(), dropoff.postalCode(), dropoff.countryCode(), dropoff.phone()),
                    order.getCurrency());
        }

        private void validateDeliveryResponse(Order order, DeliveryCreateResponse response) {
            if (response == null
                    || response.id() == null
                    || !OBJECT_ID.matcher(response.id()).matches()
                    || !order.getId().equalsIgnoreCase(response.orderId())
                    || !order.getRestaurantId().equalsIgnoreCase(response.restaurantId())
                    || !order.getCustomerId().equalsIgnoreCase(response.customerId())
                    || !order.getCurrency().equals(response.currency())
                    || !"PENDING_ASSIGNMENT".equals(response.status())) {
                throw new UpstreamResponseException();
            }
        }
    public Order applyDeliveryStatus(String orderId, DeliveryStatusCallbackRequest callback) {
        String normalizedOrderId = normalizeObjectId(orderId, "Order ID is invalid");
        String deliveryId = normalizeObjectId(callback.deliveryId(), "Delivery ID is invalid");
        if (callback.occurredAt().isAfter(Instant.now().plusSeconds(30))) {
            throw new OrderConflictException("Delivery event timestamp is invalid");
        }
        Instant eventTime = callback.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        OrderDeliveryEvent event = new OrderDeliveryEvent(
                callback.eventId(), deliveryId, callback.status(), eventTime);
        Order order = orderRepository.findById(normalizedOrderId)
                .orElseThrow(ResourceNotFoundException::new);

        var duplicate = order.getDeliveryEvents().stream()
                .filter(existing -> existing.eventId().equals(callback.eventId()))
                .findFirst();
        if (duplicate.isPresent()) {
            if (duplicate.get().equals(event)) {
                return order;
            }
            throw new OrderConflictException("Delivery event ID was already used with different data");
        }
        if (order.getDeliveryEvents().size() >= MAX_DELIVERY_EVENTS) {
            throw new OrderConflictException("Order delivery event history reached its supported limit");
        }
        if (!deliveryId.equalsIgnoreCase(order.getDeliveryId())
                || eventTime.isBefore(order.getCreatedAt())) {
            throw new OrderConflictException("Delivery event does not match this order");
        }

        List<OrderStatus> expectedStatuses = switch (callback.status()) {
            case "OUT_FOR_DELIVERY" -> List.of(OrderStatus.READY_FOR_PICKUP);
            case "DELIVERED" -> List.of(OrderStatus.OUT_FOR_DELIVERY);
            case "CANCELLED" -> List.of(
                    OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
            default -> throw new OrderConflictException("Delivery status is not supported");
        };
        if ("CANCELLED".equals(callback.status())
                && order.getCancellationStatus() != CancellationStatus.PENDING) {
            throw new OrderConflictException("Order has no pending delivery cancellation");
        }
        OrderStatus nextStatus = OrderStatus.valueOf(callback.status());
        Instant now = Instant.now();
        Update update = new Update()
                .set("status", nextStatus)
                .set("updatedAt", now)
                .set("statusUpdatedAt", eventTime)
                .addToSet("deliveryEvents", event);
        if ("CANCELLED".equals(callback.status())) {
            update.set("cancellationStatus", CancellationStatus.COMPLETE);
        }
        Order changed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(normalizedOrderId)
                        .and("deliveryId").is(deliveryId)
                        .and("status").in(expectedStatuses)
                        .and("deliveryEvents.eventId").ne(callback.eventId())),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Order.class);
        if (changed != null) {
            return changed;
        }
        Order latest = orderRepository.findById(normalizedOrderId)
                .orElseThrow(ResourceNotFoundException::new);
        var existingEvent = latest.getDeliveryEvents().stream()
                .filter(existing -> existing.eventId().equals(callback.eventId()))
                .findFirst();
        if (existingEvent.isPresent() && existingEvent.get().equals(event)) {
            return latest;
        }
        throw new OrderConflictException("Delivery status transition is stale or invalid");
    }

    private List<CreateOrderItemRequest> normalizeItems(List<CreateOrderItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 50) {
            throw new InvalidOrderException("An order must contain between 1 and 50 items");
        }
        Set<String> ids = new HashSet<>();
        List<CreateOrderItemRequest> normalized = new ArrayList<>(items.size());
        for (CreateOrderItemRequest item : items) {
            String id = normalizeObjectId(item.getMenuItemId(), "Menu item ID is invalid");
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 100) {
                throw new InvalidOrderException("Item quantities must be between 1 and 100");
            }
            if (!ids.add(id)) {
                throw new InvalidOrderException("A menu item may appear only once in an order");
            }
            CreateOrderItemRequest copy = new CreateOrderItemRequest();
            copy.setMenuItemId(id);
            copy.setQuantity(item.getQuantity());
            normalized.add(copy);
        }
        return List.copyOf(normalized);
    }

    private List<OrderItemSnapshot> validateAndSnapshot(
            String restaurantId,
            List<CreateOrderItemRequest> requestedItems,
            RestaurantQuoteResponse quote) {
        if (quote.items().size() != requestedItems.size()) {
            throw new UpstreamResponseException();
        }
        var requestedById = requestedItems.stream().collect(
                java.util.stream.Collectors.toMap(
                        item -> item.getMenuItemId().toLowerCase(Locale.ROOT), item -> item));
        Set<String> returnedIds = new HashSet<>();
        List<OrderItemSnapshot> snapshots = new ArrayList<>();
        for (RestaurantQuoteResponse.Item quoteItem : quote.items()) {
            if (quoteItem == null
                    || quoteItem.id() == null
                    || !OBJECT_ID.matcher(quoteItem.id()).matches()
                    || quoteItem.restaurantId() == null
                    || !restaurantId.equalsIgnoreCase(quoteItem.restaurantId())
                    || quoteItem.name() == null
                    || quoteItem.name().isBlank()
                    || quoteItem.available() == null
                    || quoteItem.quantity() == null
                    || quoteItem.currency() == null
                    || quoteItem.price() == null) {
                throw new UpstreamResponseException();
            }
            String itemId = quoteItem.id().toLowerCase(Locale.ROOT);
            CreateOrderItemRequest requested = requestedById.get(itemId);
            if (requested == null || !returnedIds.add(itemId)
                    || !requested.getQuantity().equals(quoteItem.quantity())) {
                throw new UpstreamResponseException();
            }
            if (!quoteItem.available()) {
                throw new InvalidOrderException("One or more menu items are unavailable");
            }
            if (!"USD".equals(quoteItem.currency())) {
                throw new InvalidOrderException("Only USD menu items can be ordered");
            }
            BigDecimal price = parsePrice(quoteItem.price());
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(requested.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            if (lineTotal.precision() > MAX_MONEY_PRECISION) {
                throw new InvalidOrderException("Order amount exceeds the supported limit");
            }
            snapshots.add(new OrderItemSnapshot(
                    itemId, quoteItem.name().trim(), requested.getQuantity(), price, lineTotal));
        }
        if (returnedIds.size() != requestedItems.size()) {
            throw new UpstreamResponseException();
        }
        return List.copyOf(snapshots);
    }

    private BigDecimal parsePrice(String value) {
        if (!PRICE_FORMAT.matcher(value).matches()) {
            throw new UpstreamResponseException();
        }
        BigDecimal price = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
        if (price.precision() > MAX_MONEY_PRECISION) {
            throw new InvalidOrderException("Menu price exceeds the supported limit");
        }
        return price;
    }

    private OrderAddressSnapshot addressSnapshot(UserAddressResponse.Address address) {
        return new OrderAddressSnapshot(
                address.id().toLowerCase(Locale.ROOT),
                address.recipientName().trim(),
                address.line1().trim(),
                normalizeOptional(address.line2()),
                address.city().trim(),
                address.region().trim(),
                address.postalCode().trim(),
                address.countryCode().trim().toUpperCase(Locale.ROOT),
                address.phone().trim());
    }

    private CreateResult replay(Order order, String fingerprint) {
        if (!order.getIdempotency().requestFingerprint().equals(fingerprint)) {
            throw new OrderConflictException("The idempotency key was already used for a different request");
        }
        return new CreateResult(order, true);
    }

    private String fingerprint(
            String restaurantId, String addressId, List<CreateOrderItemRequest> items) {
        String canonical = restaurantId + "|" + addressId + "|"
                + items.stream()
                        .sorted(Comparator.comparing(item -> item.getMenuItemId().toLowerCase(Locale.ROOT)))
                        .map(item -> item.getMenuItemId().toLowerCase(Locale.ROOT) + ":" + item.getQuantity())
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("");
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeObjectId(String value, String message) {
        if (value == null || !OBJECT_ID.matcher(value).matches()) {
            throw new InvalidOrderException(message);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateResult(Order order, boolean replayed) {
    }
}
