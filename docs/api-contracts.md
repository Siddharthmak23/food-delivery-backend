# FoodieExpress API Contracts

This document distinguishes implemented endpoints from planned API surface. Public APIs are JSON over HTTPS (HTTP only for local development), use camelCase JSON, and share the error and pagination rules here. IDs are MongoDB ObjectId values represented as 24-character hexadecimal strings. Dates/times are ISO 8601 UTC strings. Monetary values are decimal strings (for example `"12.50"`), never JSON floating-point values.

## Shared conventions

### Authentication

- `Public`: no JWT required.
- `Customer`: valid user JWT with `CUSTOMER`.
- `User`: valid user JWT; resource ownership still applies.
- `Owner`: valid user JWT with `RESTAURANT_OWNER`, plus verified ownership of the specific restaurant.
- `Admin`: valid user JWT with `ADMIN`.
- `Delivery`: valid user JWT with `DELIVERY_OPERATOR` or `ADMIN`, subject to the record/transition rules.
- `Internal`: short-lived service JWT with the required service identity, audience, and scope; not accepted as user authentication.

User JWTs are issued by User Service and independently verified by each service. Public registration always assigns `CUSTOMER`; request-supplied roles are rejected/ignored per validation policy, never honored. Internal endpoints are not public browser APIs.

### Success and error formats

Successful resources use their named object directly, except collections, which use:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "sort": "createdAt,desc"
}
```

Page numbering is zero-based. `size` defaults to 20 and is capped at 100. Sort keys are endpoint-whitelisted; invalid sort fields or directions return `400`. Default collection ordering is deterministic (normally `createdAt,desc` or `name,asc`).

All errors use:

```json
{
  "timestamp": "2026-10-22T09:15:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "fieldErrors": [
    { "field": "quantity", "code": "Min", "message": "must be greater than or equal to 1" }
  ],
  "traceId": "request-correlation-id"
}
```

`fieldErrors` is omitted when there are no field-level errors. Messages are safe for clients; stack traces and secrets are never returned. Common statuses: `400` malformed/invalid input, `401` unauthenticated, `403` forbidden, `404` missing or deliberately concealed resource, `409` state/uniqueness/idempotency conflict, `422` semantically invalid business request, `429` rate limited if configured, `502` upstream failure, `503` unavailable dependency, and `500` unexpected server failure.

### General validation and failures

Request DTOs use server-side Bean Validation. Invalid ObjectIds, unknown enum values, missing required values, invalid page/sort fields, negative monetary values, quantities outside the documented range, malformed JSON, and missing required headers return `400`. Authorization and ownership failures return `403` or a concealed `404`, consistently per resource. Dependency timeouts do not become success-shaped empty results; they return an explicit `503`/`502` error with a trace ID.

## User Service — port 8081

Public registration trims and lowercases email before storage, requires a password of 12–128 characters, and never accepts client-supplied role fields. User Service signs HS256 access tokens; all services validate them with the same environment-specific `JWT_SECRET`, configured as Base64-encoded random bytes of at least 32 bytes. Issuer, audience, identity claims, signature, and expiry are checked; access-token lifetime is capped at one hour. All services must use separate secrets per deployment environment.

| Method and path | Purpose; access | Request | Success and expected status |
|---|---|---|---|
| `POST /api/auth/register` | Register customer; Public | `{ "email": "a@example.com", "password": "...", "displayName": "A" }`. Role fields are not accepted. | `201 { "id", "email", "displayName", "roles": ["CUSTOMER"], "createdAt" }`; `400`, `409` email already used. |
| `POST /api/auth/login` | Authenticate; Public | `{ "email", "password" }` | `200 { "accessToken", "tokenType": "Bearer", "expiresAt", "user": { "id", "email", "displayName", "roles" } }`; `400`, `401` generic invalid credentials. |
| `GET /api/users/me` | Read own profile; User | None | `200 { "id", "email", "displayName", "phone": null, "roles", "createdAt" }`; `401`. |
| `PUT /api/users/me` | Replace editable own profile fields; User | `{ "displayName": "A", "phone": "+1..." }` | `200` profile object; `400`, `401`. Email and roles are not writable here. |
| `GET /api/users/me/addresses` | List own addresses; User | `page`, `size`, optional allowed sort (default `createdAt,desc`) | `200` paginated address objects; `400`, `401`. |
| `POST /api/users/me/addresses` | Add own Indian address; User | `{ "label": "Home", "recipientName": "A", "line1": "...", "line2": null, "city": "Mumbai", "region": "Maharashtra", "postalCode": "400001", "countryCode": "IN", "phone": "+91 98765-43210" }`. Region must be an Indian state or union territory, PIN code six digits, and phone an Indian mobile number. | `201` address object with normalized `+91` phone; `400`, `401`. |
| `PUT /api/users/me/addresses/{id}` | Replace an owned address; User | Same editable address fields | `200` address object; `400`, `401`, `404` not owned/not found. |
| `DELETE /api/users/me/addresses/{id}` | Delete an owned address; User | None | `204`; `401`, `404`. Deletion does not alter order snapshots. |
| `GET /internal/users/{userId}/addresses/{addressId}` | Implemented: return an address for checkout; Order Service internal JWT with `user:address:read` scope | None | `200 { "userId", "address": { "id", "recipientName", "line1", "line2", "city", "region", "postalCode", "countryCode", "phone" } }`; `401`, `403`, `404`, `503`. User Service verifies the address belongs to the requested active user. Internal response is not exposed to browser. |
| `GET /api/admin/users` | Paginated user management; Admin | `page`, `size`, optional `email`, allowed sort | `200` paginated user summaries without credentials; `400`, `401`, `403`. |
| `PATCH /api/admin/users/{id}/status` | Disable/enable account; Admin | `{ "status": "ACTIVE" }` or `"DISABLED"` | `200` user summary; `400`, `401`, `403`, `404`, `409` invalid state. Role assignment is not performed by this route. |
| `PATCH /api/admin/users/{id}/roles` | Grant/revoke approved platform roles; Admin | `{ "roles": ["CUSTOMER", "RESTAURANT_OWNER"] }`; granting `ADMIN` requires an existing administrator and is audited | `200` user summary; `400`, `401`, `403`, `404`, `409`. Public registration cannot call or emulate this operation. |
| `POST /internal/auth/service-token` | Planned centralized service-token issuance; not implemented | `{ "serviceId": "order-service", "audience": "restaurant-service", "scopes": ["menu:read"] }` | Planned `200 { "accessToken", "expiresAt" }`; `400`, `401`, `403`. Current Order-to-Restaurant credentials are provisioned out of band and signed by Order Service; this route is not available. |

## Restaurant Service — port 8082

| Method and path | Purpose; access | Request/query | Success and expected status |
|---|---|---|---|
| `GET /api/restaurants` | Discover approved, active restaurants; Public | `page`, `size`, optional `q` (name/description text search), `cuisine`, `sort=name,asc|desc` (default `name,asc`) | `200` paginated restaurant summaries; `400`, `503`. |
| `GET /api/restaurants/{id}` | Restaurant details; Public | None | `200 { "id", "name", "description", "cuisines", "address": { public location fields }, "status": "ACTIVE" }`; `400`, `404`. Only approved active restaurants are visible. |
| `GET /api/restaurants/{id}/menu` | Browse visible restaurant menu; Public | `page`, `size`, optional `available`, `sort=name,asc|desc` (default `name,asc`) | `200` paginated menu item summaries with current decimal-string prices; `400`, `404`. |
| `GET /api/menu-items/{id}` | Read item belonging to a visible restaurant; Public | None | `200 { "id", "restaurantId", "name", "description", "category", "price", "currency": "INR", "available" }` for newly priced items; legacy stored currencies remain unchanged and cannot be ordered; `400`, `404`. |
| `POST /api/restaurants` | Register restaurant; Owner | `{ "name", "description", "cuisines": [], "address": { ... } }` | `201` restaurant object with `PENDING_APPROVAL`; `400`, `401`, `403`. Owner is derived from JWT subject, never request data. |
| `PUT /api/restaurants/{id}` | Update owned restaurant profile; Owner | Editable profile fields; status/owner cannot be set here | `200` restaurant; `400`, `401`, `403`, `404`. |
| `POST /api/restaurants/{id}/menu-items` | Create item for owned restaurant; Owner | `{ "name", "description", "category", "price": "250.00", "currency": "INR", "available": true }` | `201` item; non-INR currency is rejected; `400`, `401`, `403`, `404`, `409` invalid duplicate policy if applicable. |
| `PUT /api/menu-items/{id}` | Update owned item's editable fields; Owner | Same item fields; `restaurantId` immutable | `200` item; `400`, `401`, `403`, `404`. |
| `PATCH /api/menu-items/{id}/availability` | Toggle owned item availability; Owner | `{ "available": false }` | `200` item; `400`, `401`, `403`, `404`. |
| `PATCH /api/admin/restaurants/{id}/approval` | Approve or deactivate restaurant; Admin | `{ "status": "ACTIVE" }` or `"REJECTED"` / `"INACTIVE"` | `200` restaurant; `400`, `401`, `403`, `404`, `409`. |
| `POST /internal/restaurants/{id}/menu-quote` | Authoritative checkout quote; Order Service internal JWT with `menu:read` scope | `{ "items": [{ "menuItemId": "<24-hex-id>", "quantity": 1 }] }`; 1–50 lines, quantity 1–100; duplicate IDs rejected | `200 { "restaurantId", "items": [{ "id", "restaurantId", "name", "quantity", "price", "currency", "available" }] }`; `400`, `401`, `403`, `404`, `503`. Prices and currency come from Restaurant Service storage; unavailable items are returned with `available:false`, while unknown items/invisible restaurants return `404`. Order Service must reject unavailable items and never trust client prices. |
| `GET /internal/restaurants/{restaurantId}/owners/{userId}` | Verify persisted restaurant ownership; Order Service internal JWT with `restaurant:ownership:read` scope | None | `200 { "restaurantId", "userId", "isOwner": true }` or `404`; `401`, `403`, `503`. A false ownership result is deliberately indistinguishable from a missing resource. |
| `GET /internal/restaurants/{id}/delivery-details` | Read restaurant pickup location for accepted-order provisioning; Order Service internal JWT with `delivery:provision` scope | None | `200 { "id", "name", "address": { "line1", "line2", "city", "region", "postalCode", "countryCode" } }`; `401`, `403`, `404`, `503`. This internal response is not public browser data. |
| `GET /api/admin/restaurants` | Platform restaurant management; Admin | `page`, `size`, optional `status`, allowed sort | `200` paginated restaurant summaries; `400`, `401`, `403`. |

Restaurant discovery excludes `PENDING_APPROVAL`, `REJECTED`, and `INACTIVE` records. Internal menu responses can identify an item as unavailable; checkout fails without creating an order if any item is missing, unavailable, from another restaurant, or has an unsupported currency. Internal endpoints accept only short-lived ES256 tokens with Order Service issuer/subject, Restaurant Service audience, issued/expiry claims, maximum two-minute lifetime, and the exact route scope. The Order Service private key must remain exclusive to Order Service; Restaurant Service receives only the matching public key. These credentials are provisioned out of band and are separate from user-token `JWT_SECRET`.

## Order Service — port 8083

**Implemented scope:** customer checkout/history/detail/cancellation, restaurant-owner order listing and lifecycle transitions, acceptance-triggered delivery provisioning with retry, administrator pre-pickup cancellation, and delivery callbacks. Order request lines contain menu item IDs and integer quantities only; unknown JSON fields are rejected. The request does not accept prices, totals, or a free-form delivery address. Order Service obtains the saved address through User Service and current item data through Restaurant Service. It accepts only INR quotes and stores order-time snapshots. Existing USD records are retained without conversion; a legacy USD menu quote is rejected for new checkout. Since the quote and order write are separate service operations, menu state can change between quote and persistence.

`POST /api/orders` requires `Idempotency-Key` (1–128 visible ASCII characters), unique per customer. Identical normalized requests under the same key return the original order (`200`); changed requests return `409`. Production must provision the unique MongoDB index documented in `database-design.md`; application replay checks alone are not race-safe.

| Method and path | Purpose; access | Request/query | Implemented response and errors |
|---|---|---|---|
| `POST /api/orders` | Create a simulated-unpaid order; Customer | Header `Idempotency-Key`; `{ "restaurantId": "...", "items": [{ "menuItemId": "...", "quantity": 2 }], "addressId": "..." }`; 1–50 distinct items, quantity 1–100 | `201` new order; replay `200`. `400`, `401`, `404` missing address/item, `409` key conflict, `422` unavailable/unsupported currency, `502` invalid upstream response, `503` dependency unavailable. |
| `GET /api/orders/my-orders` | Read authenticated customer's history; Customer | `page` default `0`, `size` default `20`, maximum `100`; fixed `createdAt,desc` ordering | `200` paginated order objects; `400`, `401`, `503`. |
| `GET /api/orders/{id}` | Read an order owned by authenticated customer; Customer | None | `200` order object; `400`, `401`, concealed `404`, `503`. |
| `PATCH /api/orders/{id}/cancel` | Cancel own order only while `PLACED`; Customer | Optional `{ "reason": "..." }` | `200` order with `CANCELLED`; `400`, `401`, concealed `404`, `409` invalid state, `503`. |
| `GET /api/orders/restaurant/{restaurantId}` | List orders for a restaurant; Owner, with persisted ownership verified by Restaurant Service | `page`, `size` | `200` paginated orders (`createdAt,asc`); `400`, `401`, `403`, `503`. |
| `PATCH /api/orders/{id}/restaurant-status` | Accept, reject, or advance preparation; Owner of the order's stored restaurant | `{ "status": "CONFIRMED" \| "REJECTED" \| "PREPARING" \| "READY_FOR_PICKUP", "reason": "..." }`; rejection reason required | `200` updated order; accepting returns `202` when delivery provisioning remains pending; `400`, `401`, `403`, `404`, `409`, `422`, `502`, `503`. `READY_FOR_PICKUP` requires a persisted delivery ID and `COMPLETE` provisioning. |
| `POST /api/orders/{id}/delivery-provisioning/retry` | Retry delivery provisioning; Admin | None | `200` when complete, `202` while still pending/retry-required; `401`, `403`, `404`, `409`, `502`, `503`. Every attempt uses the order ID as the Delivery Service idempotency key. |
| `PATCH /api/admin/orders/{id}/cancel` | Cancel an accepted order before pickup; Admin | `{ "reason": "..." }` | `200` after Delivery Service cancellation callback is applied; `400`, `401`, `403`, `404`, `409`, `503`. A failed call leaves explicit pending/retry-required cancellation state. |

Checkout computes `lineTotal = quantity × unitPrice` using `BigDecimal` at two decimal places and sums line totals for the INR `subtotal`. New orders have a zero delivery fee and zero discount, so `total = subtotal`; tax is not calculated. Initial status is `PLACED`; payment status is `SIMULATED_UNPAID`; delivery provisioning starts at `NOT_REQUESTED`. Customer cancellation is an atomic compare-and-set from `PLACED` and repeat cancellation returns the resulting order. Acceptance persists `CONFIRMED` and `PENDING` before delivery provisioning; delivery ID is recorded only after the response is checked against order, restaurant, customer, currency, and expected delivery status. Provisioning failure keeps the order confirmed and records `RETRY_REQUIRED`; an identical retry is safe because Delivery Service is idempotent by order ID. Restaurant ownership is checked against persisted Restaurant Service data, not a caller-supplied restaurant association. No payment is processed.

Order-to-User address lookup uses a separate Order-to-User P-256 ES256 key pair and scope `user:address:read`; Order-to-Restaurant calls retain their separate key pair and route-specific scopes, including `delivery:provision` for pickup-location lookup. Order-to-Delivery provisioning and cancellation retain their separate key pair and `delivery:create` / `delivery:cancel` scopes. These use short-lived service identities, fixed issuer/audience, signature validation, required time claims, and a two-minute maximum lifetime. None use or replace the HS256 user-token secret.

## Delivery Service — port 8084

**Implemented Phase 6 and lifecycle integration scope:** Delivery creation, delivery/admin assignment and lifecycle, customer/operator/admin reads, cancellation before pickup, timeline events, and signed status callbacks to Order Service. Order Service now creates deliveries after restaurant acceptance and stores the validated delivery ID. Restaurant-owner delivery reads are not part of the documented contract and remain unavailable; a role claim alone is not an ownership proof.

| Method and path | Purpose; access | Request/query | Success and expected status |
|---|---|---|---|
| `POST /api/deliveries` | Create delivery from an accepted order; Internal `order-service` token with `delivery:create` | Header `Idempotency-Key` equal to the 24-hex `orderId`; `{ "orderId", "restaurantId", "customerId", "pickupAddress": { ... }, "deliveryAddress": { ... }, "currency": "INR" }`. Pickup `phone` is nullable because Restaurant Service does not currently store one; customer dropoff phone is required. | `201` new or `200` identical replay; non-INR currency is rejected; `400`, `401`, `403`, `409` conflicting duplicate payload, `503`. Not a public customer endpoint. |
| `GET /api/deliveries/{id}` | Read delivery; owning Customer, Delivery Operator, or Admin | None | `200` delivery object; `401`, `403`, `404`. Restaurant-owner read access is not implemented. |
| `GET /api/deliveries/order/{orderId}` | Find delivery for order; same access checks | None | `200` delivery object; `404` when not yet provisioned; `401`, `403`. |
| `PATCH /api/deliveries/{id}/assign` | Assign a simulated partner; Delivery or Admin | `{ "partnerId": "demo-partner-01" }` | `200` delivery; `400`, `401`, `403`, `404`, `409` invalid status/unknown demo partner. |
| `PATCH /api/deliveries/{id}/status` | Record pickup or delivery completion; Delivery Operator or Admin | `{ "status": "PICKED_UP" }` or `"DELIVERED"` | `200` delivery; `400`, `401`, `403`, `404`, `409` invalid transition, `503` when the Order callback fails after the event is persisted. |
| `PATCH /api/deliveries/{id}/cancel` | Cancel before pickup; Internal `order-service` token with `delivery:cancel`, or Admin | `{ "reason": "..." }` | `200` cancelled delivery; `400`, `401`, `403`, `404`, `409` pickup already occurred, `503` if the persisted cancellation callback fails. A repeat retries the pending callback. |
| `GET /api/deliveries/{id}/timeline` | Read timeline; same access checks as delivery detail | None | `200 { "deliveryId", "events": [{ "id", "type", "occurredAt", "actorId", "source", "callbackPending" }] }`; `401`, `403`, `404`. |
| `POST /internal/orders/{id}/delivery-status` | Apply a Delivery Service status callback; Internal `delivery-service` token with `order:delivery-status:write` | `{ "deliveryId", "eventId", "status": "OUT_FOR_DELIVERY" \| "DELIVERED" \| "CANCELLED", "occurredAt" }` | `200` updated order; `401`, `403`, `404`, `409`, `503`. Event IDs are idempotent; delivery ID, current order state, cancellation state, and event time are checked. |

## Coordination and status ownership

Order Service is authoritative for order state; Restaurant Service owns restaurant/menu data; Delivery Service owns delivery assignment/status/events. Delivery creation is protected by a scoped Order Service ES256 token and is idempotent by `orderId`; the provisioned payload must match for a replay to succeed. Delivery pickup/completion/cancellation events are persisted first, then reported to Order Service using a separate Delivery-to-Order ES256 trust boundary; repeated event IDs are harmless. No service reads another service's database. Restaurant-owner delivery-detail reads are not exposed because no such public capability is required by the current documented workflow.

The full transitions, timeout scenario, cancellation, and recovery sequence are defined in [order-workflow.md](order-workflow.md).
