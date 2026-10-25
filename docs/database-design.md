# FoodieExpress Database Design

All collections are planned and owned by exactly one service. Each service uses its own logical MongoDB database. IDs are MongoDB ObjectIds serialized as 24-character hex strings at API boundaries. Cross-service references are IDs only; there are no cross-database joins or direct collection access.

Money is represented in Java as `BigDecimal`, encoded in MongoDB as BSON Decimal128. Extended JSON examples use `{"$numberDecimal":"12.50"}`. API contracts serialize money as decimal strings. New prices, orders, and deliveries use ISO 4217 `INR`; no Java `float` or `double` is used for currency. Existing USD documents are historical legacy data: this implementation does not convert, relabel, or rewrite them. Legacy menu prices must be explicitly repriced by an authorized owner before they can be ordered.

Dates are BSON dates stored in UTC; APIs emit ISO 8601 UTC strings. MongoDB schema validation should enforce required fields, types, enum values, and basic ranges where practical, in addition to Java Bean Validation.

## User Service — `food_user`

### `users`

```json
{
  "_id": { "$oid": "671000000000000000000001" },
  "email": "customer@example.com",
  "passwordHash": "one-way-hash",
  "displayName": "Customer",
  "phone": null,
  "roles": ["CUSTOMER"],
  "status": "ACTIVE",
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

`_id`, `email`, `passwordHash`, `displayName`, `roles`, `status`, `createdAt`, and `updatedAt` are required. `phone` is optional. Email is normalized consistently (trimmed and case-normalized) before uniqueness checks. Roles are controlled by protected workflows; public registration creates only `CUSTOMER`. Password hash is never returned or logged.

**Indexes/queries:** unique index on normalized `email`; indexes on `status` and role only if supported admin queries justify them. Login queries by email; profile lookup by `_id`; administrative lists filter by status/role and are paginated.

### `addresses`

Addresses are stored in a separate collection rather than embedded in `users`: each address can be updated/deleted independently, has an authorization boundary, and user documents remain bounded as address count changes.

```json
{
  "_id": { "$oid": "671000000000000000000010" },
  "userId": { "$oid": "671000000000000000000001" },
  "label": "Home",
  "recipientName": "Customer",
  "line1": "10 Example St",
  "line2": null,
  "city": "Mumbai",
  "region": "Maharashtra",
  "postalCode": "400001",
  "countryCode": "IN",
  "phone": "+919876543210",
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

All fields except `line2` are required. Address data is private; internal lookup is restricted to Order Service and the address owner is rechecked before checkout. Index `{ userId: 1, createdAt: -1, _id: -1 }` supports stable address listing; `{ _id: 1, userId: 1 }` supports scoped ownership checks. Addresses are not embedded in orders by reference alone; a sanitized snapshot is stored in each order.

## Restaurant Service — `food_restaurant`

### `restaurants`

```json
{
  "_id": { "$oid": "672000000000000000000001" },
  "ownerUserId": { "$oid": "671000000000000000000001" },
  "name": "Sample Kitchen",
  "description": "Example restaurant",
  "cuisines": ["Italian"],
  "address": {
    "line1": "20 Market St",
    "line2": null,
    "city": "Example City",
    "region": "CA",
    "postalCode": "90000",
    "countryCode": "US"
  },
  "status": "PENDING_APPROVAL",
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

All shown fields are required except `description` and `address.line2`. `ownerUserId` is the User Service identifier. Status is one of `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, or `INACTIVE`. Public discovery returns only `ACTIVE` restaurants.

**Indexes/queries:** `{ status: 1, name: 1, _id: 1 }` for stable discovery sorting; `{ ownerUserId: 1, status: 1 }` for owner/admin views; multikey index on `cuisines` for cuisine filtering. Phase 4 uses a MongoDB text index over `name` and `description` for `q` discovery filters. Search results are sorted by name and ID, not by text relevance; index behavior and performance must be measured before making scale claims. Indexes are provisioned explicitly using the commands in the README; application startup does not create them.

### `menuItems`

```json
{
  "_id": { "$oid": "672000000000000000000010" },
  "restaurantId": { "$oid": "672000000000000000000001" },
  "name": "Pasta",
  "description": "Example menu item",
  "category": "Main",
  "price": { "$numberDecimal": "12.50" },
  "currency": "INR",
  "available": true,
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

`_id`, `restaurantId`, `name`, `category`, `price`, `currency`, `available`, and timestamps are required; description is optional. Price is non-negative, has at most two fractional digits, and is Decimal128. Restaurant association is immutable after creation.

**Indexes/queries:** `{ restaurantId: 1, available: 1, name: 1, _id: 1 }` supports menu browsing and checkout; `{ _id: 1, restaurantId: 1 }` supports scoped ownership. Menu item count can grow with restaurant activity; no historical order relies on current menu records.

## Order Service — `food_order`

### `orders`

```json
{
  "_id": { "$oid": "673000000000000000000001" },
  "customerId": { "$oid": "671000000000000000000001" },
  "restaurantId": { "$oid": "672000000000000000000001" },
  "deliveryId": null,
  "deliveryEvents": [],
  "items": [
    {
      "menuItemId": { "$oid": "672000000000000000000010" },
      "name": "Pasta",
      "quantity": 2,
      "unitPrice": { "$numberDecimal": "12.50" },
      "lineTotal": { "$numberDecimal": "25.00" }
    }
  ],
  "deliveryAddress": {
    "addressId": { "$oid": "671000000000000000000010" },
    "recipientName": "Customer",
    "line1": "10 Example St",
    "line2": null,
    "city": "Example City",
    "region": "CA",
    "postalCode": "90000",
    "countryCode": "US",
    "phone": "+10000000000"
  },
  "currency": "INR",
  "subtotal": { "$numberDecimal": "25.00" },
  "deliveryFee": { "$numberDecimal": "0.00" },
  "discount": { "$numberDecimal": "0.00" },
  "total": { "$numberDecimal": "25.00" },
  "paymentStatus": "SIMULATED_UNPAID",
  "status": "PLACED",
  "deliveryProvisioningStatus": "NOT_REQUESTED",
  "cancellationStatus": "NONE",
  "cancellationReason": null,
  "rejectionReason": null,
  "idempotency": {
    "key": "client-generated-key",
    "requestFingerprint": "sha256-of-normalized-request"
  },
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" },
  "statusUpdatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

Required fields include all top-level fields shown; `deliveryId` is null until an accepted order's delivery is successfully validated and associated, and `deliveryEvents` starts empty. `cancellationReason` and `rejectionReason` are optional/null and only populated by the corresponding transition. `deliveryProvisioningStatus` is `NOT_REQUESTED`, `PENDING`, `RETRY_REQUIRED`, or `COMPLETE`; it starts as `NOT_REQUESTED` and is always persisted. `cancellationStatus` is `NONE`, `PENDING`, `RETRY_REQUIRED`, or `COMPLETE`. Each delivery event contains `eventId`, `deliveryId`, callback `status`, and UTC `occurredAt`; event IDs are idempotency keys. Each item requires a menu ID, checkout-time name, positive integer quantity, unit price, and line total. `deliveryAddress` is a sanitized immutable snapshot plus source `addressId`; it must not be reloaded to display historical orders. Price snapshots are copied from Restaurant Service at checkout, never reconstructed from the current menu. Totals and snapshots are immutable after creation; lifecycle, cancellation, and provisioning fields may change.

**Current pricing policy:** all new menu items and orders use INR; prices have two decimal places; line total is quantity × unit price rounded to two decimals using `BigDecimal` with `HALF_UP`; subtotal is the exact sum of rounded line totals; delivery fee and discount are both `₹0.00`, with no tax calculation. `total = subtotal + deliveryFee - discount`. The Order Service calculates every value server-side. Checkout is simulated and `paymentStatus` is always `SIMULATED_UNPAID`; no payment authorization, settlement, or external INR payment processing occurs. Historical USD documents retain their stored currency and amounts.

**Indexes/queries:** The Order model declares `customer_history_idx` (`{ customerId: 1, createdAt: -1, _id: -1 }`), `restaurant_history_idx` (`{ restaurantId: 1, createdAt: 1, _id: 1 }`), `restaurant_status_created_idx` (`{ restaurantId: 1, status: 1, createdAt: 1 }`), `status_created_idx` (`{ status: 1, createdAt: 1 }`), and unique `customer_idempotency_unique` (`{ customerId: 1, "idempotency.key": 1 }`). Application startup has automatic index creation disabled; these must be provisioned before serving production checkout. `restaurant_history_idx` supports owner-scoped listing; the restaurant/status index supports status-filtered operational queries. Delivery callbacks use the persisted `deliveryId` and bounded `deliveryEvents`. Bound item count, quantity, and callback events to prevent unbounded documents. Orders are historical records; retention/deletion policy must be decided before production.

Provision the indexes in the service-owned database (replace the URI with the deployment's protected connection string as needed):

```sh
mongosh "mongodb://localhost:27017/food_order" --eval '
db.orders.createIndex(
  { customerId: 1, createdAt: -1, _id: -1 },
  { name: "customer_history_idx" }
);
db.orders.createIndex(
  { restaurantId: 1, createdAt: 1, _id: 1 },
  { name: "restaurant_history_idx" }
);
db.orders.createIndex(
  { restaurantId: 1, status: 1, createdAt: 1 },
  { name: "restaurant_status_created_idx" }
);
db.orders.createIndex(
  { status: 1, createdAt: 1 },
  { name: "status_created_idx" }
);
db.orders.createIndex(
  { customerId: 1, "idempotency.key": 1 },
  { name: "customer_idempotency_unique", unique: true }
);'
```

The unique index creation fails if existing data contains duplicate customer/key pairs; resolve duplicates before provisioning rather than relying on application-level checks. Integration tests explicitly create the unique index and customer-history index in their dedicated `food_order_phase5_test` database. They do not provision the developer's normal database.

## Delivery Service — `food_delivery`

### `deliveries`

Delivery timeline events are embedded in the delivery document for MVP because the expected event count per delivery is small and timeline reads are normally requested with the delivery. Bound event count and document size; if timeline volume grows substantially, revisit a separate event collection.

```json
{
  "_id": { "$oid": "674000000000000000000001" },
  "orderId": { "$oid": "673000000000000000000001" },
  "restaurantId": { "$oid": "672000000000000000000001" },
  "customerId": { "$oid": "671000000000000000000001" },
  "pickupAddress": { "line1": "20 Market St", "city": "Example City", "region": "CA", "postalCode": "90000", "countryCode": "US" },
  "deliveryAddress": { "recipientName": "Customer", "line1": "10 Example St", "line2": null, "city": "Example City", "region": "CA", "postalCode": "90000", "countryCode": "US", "phone": "+10000000000" },
  "partner": { "partnerId": "demo-partner-01", "displayName": "Demo Partner 1" },
  "status": "PENDING_ASSIGNMENT",
  "events": [
    { "eventId": "event-1", "type": "CREATED", "occurredAt": { "$date": "2026-10-22T09:00:00Z" }, "actorId": "order-service", "source": "ORDER_SERVICE", "callbackPending": false }
  ],
  "orderCallbackPending": false,
  "createdAt": { "$date": "2026-10-22T09:00:00Z" },
  "updatedAt": { "$date": "2026-10-22T09:00:00Z" }
}
```

`_id`, `orderId`, `restaurantId`, `customerId`, address snapshots, `status`, `events`, callback state, and timestamps are required. Partner is optional until assignment. Addresses are copied from the accepted order for operational use; this data is private and must not be included in logs. The MVP partner is selected from a finite configured/demo set; this is not integration with real couriers.

**Indexes/queries:** unique `{ orderId: 1 }` enforces one delivery per order; `{ customerId: 1, createdAt: -1 }` and `{ restaurantId: 1, createdAt: -1 }` support authorized histories; `{ status: 1, createdAt: 1 }` supports unassigned work. Event IDs are unique within a delivery and used for idempotent retries. Delivery documents and address snapshots are subject to a retention policy to be determined before production.

Automatic index creation is disabled. Provision the service indexes explicitly in `food_delivery`:

```sh
mongosh "mongodb://localhost:27017/food_delivery" --eval '
db.deliveries.createIndex({ orderId: 1 }, { name: "delivery_order_unique", unique: true });
db.deliveries.createIndex({ customerId: 1, createdAt: -1 }, { name: "delivery_customer_created_idx" });
db.deliveries.createIndex({ restaurantId: 1, createdAt: -1 }, { name: "delivery_restaurant_created_idx" });
db.deliveries.createIndex({ status: 1, createdAt: 1 }, { name: "delivery_status_created_idx" });'
```

The unique order index is required for race-safe provisioning idempotency. The Order callback records event IDs in the existing order document's `deliveryEvents` array; the Delivery Service stores callback status on each embedded event and the delivery document.

## Validation and growth considerations

Use required/type/enum/range rules at both request DTO and database schema boundaries. Validate ObjectId syntax before querying, validate relationships through owning services, and enforce unique constraints in MongoDB rather than relying on pre-checks alone. Paginate unbounded lists. Keep embedded arrays bounded (`orders.items`, `deliveries.events`). Historical order and delivery snapshots are immutable records; profile/menu changes must not rewrite them.
