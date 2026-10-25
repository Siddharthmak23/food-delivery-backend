package com.foodieexpress.deliveryservice.service;

import com.foodieexpress.deliveryservice.dto.AssignDeliveryRequest;
import com.foodieexpress.deliveryservice.dto.CreateDeliveryRequest;
import com.foodieexpress.deliveryservice.dto.DeliveryAddressRequest;
import com.foodieexpress.deliveryservice.dto.PageResponse;
import com.foodieexpress.deliveryservice.dto.PickupAddressRequest;
import com.foodieexpress.deliveryservice.exception.DeliveryConflictException;
import com.foodieexpress.deliveryservice.exception.DeliveryNotFoundException;
import com.foodieexpress.deliveryservice.exception.InvalidDeliveryException;
import com.foodieexpress.deliveryservice.model.Delivery;
import com.foodieexpress.deliveryservice.model.DeliveryAddress;
import com.foodieexpress.deliveryservice.model.DeliveryEvent;
import com.foodieexpress.deliveryservice.model.DeliveryPartner;
import com.foodieexpress.deliveryservice.model.DeliveryStatus;
import com.foodieexpress.deliveryservice.repository.DeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    private static final Pattern OBJECT_ID = Pattern.compile("^[a-fA-F0-9]{24}$");
    private static final int MAX_EVENTS = 100;

    private final DeliveryRepository repository;
    private final MongoTemplate mongoTemplate;
    private final OrderStatusClient orderStatusClient;
    private final Map<String, String> partners;

    public DeliveryService(
            DeliveryRepository repository,
            MongoTemplate mongoTemplate,
            OrderStatusClient orderStatusClient,
            @org.springframework.beans.factory.annotation.Value("${app.delivery.demo-partners:demo-partner-01:Demo Partner 1}")
                    String configuredPartners) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.orderStatusClient = orderStatusClient;
        this.partners = parsePartners(configuredPartners);
    }

    public CreateResult create(String idempotencyKey, CreateDeliveryRequest request) {
        String orderId = normalizeId(request.orderId(), "Order ID is invalid");
        if (!orderId.equalsIgnoreCase(idempotencyKey)) {
            throw new InvalidDeliveryException("Idempotency-Key must match the order ID");
        }
        String restaurantId = normalizeId(request.restaurantId(), "Restaurant ID is invalid");
        String customerId = normalizeId(request.customerId(), "Customer ID is invalid");
        if (!"INR".equals(request.currency())) {
            throw new InvalidDeliveryException("Only INR deliveries are supported");
        }
        DeliveryAddress pickup = pickupAddress(request.pickupAddress());
        DeliveryAddress dropoff = address(request.deliveryAddress());

        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return new CreateResult(existingRequest(existing.get(), restaurantId, customerId, pickup, dropoff), true);
        }

        Instant now = Instant.now();
        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setRestaurantId(restaurantId);
        delivery.setCustomerId(customerId);
        delivery.setPickupAddress(pickup);
        delivery.setDeliveryAddress(dropoff);
        delivery.setCurrency(request.currency());
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        delivery.setEvents(List.of(event("CREATED", now, "order-service", "ORDER_SERVICE", false)));
        delivery.setOrderCallbackPending(false);
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        try {
            return new CreateResult(repository.save(delivery), false);
        } catch (DuplicateKeyException exception) {
            return repository.findByOrderId(orderId)
                    .map(saved -> new CreateResult(
                            existingRequest(saved, restaurantId, customerId, pickup, dropoff), true))
                    .orElseThrow(() -> new DeliveryConflictException("A delivery already exists for this order"));
        }
    }

    public Delivery getForUser(String id, Jwt jwt) {
        Delivery delivery = repository.findById(normalizeId(id, "Delivery ID is invalid"))
                .orElseThrow(DeliveryNotFoundException::new);
        authorizeRead(delivery, jwt);
        return delivery;
    }

    public Delivery getByOrderForUser(String orderId, Jwt jwt) {
        Delivery delivery = repository.findByOrderId(normalizeId(orderId, "Order ID is invalid"))
                .orElseThrow(DeliveryNotFoundException::new);
        authorizeRead(delivery, jwt);
        return delivery;
    }

    public PageResponse<Delivery> listForOperator(String operatorId, int page, int size, Sort.Direction direction) {
        String normalizedOperatorId = normalizeId(operatorId, "Operator ID is invalid");
        Sort sort = Sort.by(direction, "updatedAt").and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by(Sort.Direction.ASC, "id"));
        Page<Delivery> result = repository.findAllByAssignedOperatorId(
                normalizedOperatorId, PageRequest.of(page, size, sort));
        return PageResponse.from(result, direction.name().equals("DESC") ? "updatedAt,desc" : "updatedAt,asc");
    }

    public Delivery assign(String id, String actorId, AssignDeliveryRequest request, boolean assignToCaller) {
        String normalizedId = normalizeId(id, "Delivery ID is invalid");
        String partnerId = request.partnerId().trim();
        String partnerName = partners.get(partnerId);
        if (partnerName == null) {
            throw new InvalidDeliveryException("The requested demo partner is not configured");
        }
        DeliveryPartner partner = new DeliveryPartner(partnerId, partnerName);
        Instant now = Instant.now();
        Update update = new Update()
                .set("status", DeliveryStatus.ASSIGNED)
                .set("partner", partner)
                .set("updatedAt", now)
                .push("events", event("ASSIGNED", now, actorId, "USER", false));
        if (assignToCaller) {
            update.set("assignedOperatorId", normalizeId(actorId, "Operator ID is invalid"));
        }
        Delivery assigned = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(normalizedId)
                        .and("status").is(DeliveryStatus.PENDING_ASSIGNMENT)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Delivery.class);
        if (assigned != null) {
            return assigned;
        }
        Delivery existing = repository.findById(normalizedId).orElseThrow(DeliveryNotFoundException::new);
        if (existing.getStatus() == DeliveryStatus.ASSIGNED && partner.equals(existing.getPartner())) {
            if (assignToCaller && existing.getAssignedOperatorId() == null) {
                existing.setAssignedOperatorId(normalizeId(actorId, "Operator ID is invalid"));
                existing.setUpdatedAt(now);
                return repository.save(existing);
            }
            return existing;
        }
        throw new DeliveryConflictException("Delivery can only be assigned while pending assignment");
    }

    public Delivery updateStatus(String id, String actorId, DeliveryStatus requested, boolean isAdmin) {
        String normalizedId = normalizeId(id, "Delivery ID is invalid");
        Delivery current = repository.findById(normalizedId).orElseThrow(DeliveryNotFoundException::new);
        if (!isAdmin && current.getAssignedOperatorId() != null
                && !current.getAssignedOperatorId().equalsIgnoreCase(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access is denied");
        }
        if (current.getStatus() == requested) {
            retryPendingCallbacks(current);
            return repository.findById(normalizedId).orElseThrow(DeliveryNotFoundException::new);
        }
        DeliveryStatus expected = switch (requested) {
            case PICKED_UP -> DeliveryStatus.ASSIGNED;
            case DELIVERED -> DeliveryStatus.PICKED_UP;
            default -> throw new DeliveryConflictException("Requested delivery status transition is not allowed");
        };
        return transition(normalizedId, expected, requested, actorId, true, null, isAdmin);
    }

    public Delivery cancel(String id, String actorId, String reason, boolean isAdmin) {
        String normalizedId = normalizeId(id, "Delivery ID is invalid");
        Delivery current = repository.findById(normalizedId).orElseThrow(DeliveryNotFoundException::new);
        if (!isAdmin && current.getAssignedOperatorId() != null
                && !current.getAssignedOperatorId().equalsIgnoreCase(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access is denied");
        }
        if (current.getStatus() == DeliveryStatus.CANCELLED) {
            retryPendingCallbacks(current);
            return repository.findById(normalizedId).orElseThrow(DeliveryNotFoundException::new);
        }
        if (current.getStatus() != DeliveryStatus.PENDING_ASSIGNMENT
                && current.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new DeliveryConflictException("Delivery cannot be cancelled after pickup");
        }
        return transition(normalizedId, current.getStatus(), DeliveryStatus.CANCELLED,
                actorId, true, reason.trim(), isAdmin);
    }

    public Delivery requireDeliveryRole(String id, Jwt jwt) {
        if (!hasRole(jwt, "DELIVERY_OPERATOR") && !hasRole(jwt, "ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Access is denied");
        }
        return repository.findById(normalizeId(id, "Delivery ID is invalid"))
                .orElseThrow(DeliveryNotFoundException::new);
    }

    private Delivery transition(
            String id,
            DeliveryStatus expected,
            DeliveryStatus target,
            String actorId,
            boolean callback,
            String cancellationReason,
            boolean isAdmin) {
        Instant now = Instant.now();
        Delivery current = repository.findById(id).orElseThrow(DeliveryNotFoundException::new);
        if (current.getEvents().size() >= MAX_EVENTS) {
            throw new DeliveryConflictException("Delivery timeline reached its supported limit");
        }
        DeliveryEvent event = event(target.name(), now, actorId, "USER", callback);
        Update update = new Update()
                .set("status", target)
                .set("updatedAt", now)
                .set("orderCallbackPending", callback);
        if (!isAdmin && current.getAssignedOperatorId() == null) {
            update.set("assignedOperatorId", normalizeId(actorId, "Operator ID is invalid"));
        }
        if (cancellationReason != null) {
            update.set("cancellationReason", cancellationReason);
        }
        update.push("events", event);
        Delivery changed = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("status").is(expected)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Delivery.class);
        if (changed == null) {
            throw new DeliveryConflictException("Delivery status changed; reload and retry");
        }
        if (callback) {
            sendPendingCallback(changed, event);
            return repository.findById(id).orElseThrow(DeliveryNotFoundException::new);
        }
        return changed;
    }

    private void retryPendingCallbacks(Delivery delivery) {
        delivery.getEvents().stream()
                .filter(DeliveryEvent::callbackPending)
                .forEach(event -> sendPendingCallback(delivery, event));
    }

    private void sendPendingCallback(Delivery delivery, DeliveryEvent event) {
        orderStatusClient.report(delivery.getOrderId(), delivery.getId(), event);
        Query query = Query.query(Criteria.where("_id").is(delivery.getId())
                .and("events").elemMatch(Criteria.where("eventId").is(event.eventId())));
        Update update = new Update()
                .set("events.$.callbackPending", false)
                .set("orderCallbackPending", false)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, Delivery.class);
    }

    private void authorizeRead(Delivery delivery, Jwt jwt) {
        String subject = jwt.getSubject();
        boolean admin = hasRole(jwt, "ADMIN");
        boolean operator = hasRole(jwt, "DELIVERY_OPERATOR");
        boolean customer = hasRole(jwt, "CUSTOMER");
        if (admin || (customer && delivery.getCustomerId().equalsIgnoreCase(subject))) {
            return;
        }
        if (operator && delivery.getAssignedOperatorId() != null
                && delivery.getAssignedOperatorId().equalsIgnoreCase(subject)) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException("Access is denied");
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private Delivery existingRequest(
            Delivery existing,
            String restaurantId,
            String customerId,
            DeliveryAddress pickup,
            DeliveryAddress dropoff) {
        if (!existing.getRestaurantId().equals(restaurantId)
                || !existing.getCustomerId().equals(customerId)
                || !existing.getPickupAddress().equals(pickup)
                || !existing.getDeliveryAddress().equals(dropoff)
                || !"INR".equals(existing.getCurrency())) {
            throw new DeliveryConflictException("Order ID was already provisioned with a different payload");
        }
        return existing;
    }

    private DeliveryAddress address(DeliveryAddressRequest request) {
        return new DeliveryAddress(
                request.recipientName().trim(),
                request.line1().trim(),
                request.line2() == null || request.line2().isBlank() ? null : request.line2().trim(),
                request.city().trim(),
                request.region().trim(),
                request.postalCode().trim(),
                request.countryCode().trim().toUpperCase(Locale.ROOT),
                request.phone().trim());
    }

    private DeliveryAddress pickupAddress(PickupAddressRequest request) {
        return new DeliveryAddress(
                request.recipientName().trim(),
                request.line1().trim(),
                request.line2() == null || request.line2().isBlank() ? null : request.line2().trim(),
                request.city().trim(),
                request.region().trim(),
                request.postalCode().trim(),
                request.countryCode().trim().toUpperCase(Locale.ROOT),
                request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
    }

    private DeliveryEvent event(
            String type, Instant at, String actorId, String source, boolean callbackPending) {
        return new DeliveryEvent(UUID.randomUUID().toString(), type, at, actorId, source, callbackPending);
    }

    private String normalizeId(String value, String message) {
        if (value == null || !OBJECT_ID.matcher(value).matches()) {
            throw new InvalidDeliveryException(message);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private Map<String, String> parsePartners(String configuredPartners) {
        if (configuredPartners == null || configuredPartners.isBlank()) {
            throw new IllegalStateException("DELIVERY_DEMO_PARTNERS must configure at least one partner");
        }
        var parsed = new java.util.LinkedHashMap<String, String>();
        for (String entry : configuredPartners.split(",")) {
            String[] values = entry.trim().split(":", 2);
            if (values.length != 2 || values[0].isBlank() || values[1].isBlank()
                    || parsed.putIfAbsent(values[0], values[1]) != null) {
                throw new IllegalStateException("DELIVERY_DEMO_PARTNERS entries must be unique id:name pairs");
            }
        }
        return Map.copyOf(parsed);
    }

    public record CreateResult(Delivery delivery, boolean replayed) {}
}
