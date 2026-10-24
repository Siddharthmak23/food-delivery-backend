# FoodieExpress Requirements

This document defines planned behavior. **MVP** means an essential requirement for the first demonstrable end-to-end release; **Later** means deliberately deferred. Nothing in this document implies that a requirement has been implemented.

## Functional requirements

| ID | Priority | Requirement |
|---|---|---|
| FR-001 | MVP | A customer can register with a unique email and password. The public registration request cannot assign privileged roles, including `ADMIN`. |
| FR-002 | MVP | A registered user can log in and receive a signed JWT with an expiry and role claims. |
| FR-003 | MVP | An authenticated user can view and update their own profile; users cannot update another user's profile. |
| FR-004 | MVP | A customer can list, add, update, and delete their own delivery addresses. Address identifiers are scoped to their owner. |
| FR-005 | MVP | A visitor or authenticated customer can discover only approved, active restaurants. |
| FR-006 | MVP | Restaurant discovery supports text search, cuisine filtering, bounded pagination, and deterministic sorting. |
| FR-007 | MVP | A user can view an approved restaurant's details and menu, including current item availability and prices. |
| FR-008 | MVP | A user can view a menu item only when it belongs to a visible restaurant. |
| FR-009 | MVP | A customer can add, remove, and change quantities of menu items in a cart held in local Redux state. The backend revalidates all cart data during checkout. |
| FR-010 | MVP | A customer can place an order by submitting menu item identifiers, quantities, a saved address identifier, and an idempotency key. The server validates prices, availability, restaurant consistency, address ownership, and totals. |
| FR-011 | MVP | A customer can view only their own order detail and paginated order history. Restaurant owners can view orders only for restaurants they own; administrators can view platform orders. |
| FR-012 | MVP | A customer can cancel an order only while its status is `PLACED`. Other cancellations require an administrator action under the defined transition policy. |
| FR-013 | MVP | A customer can track their order status and, once available, its associated delivery status and timeline. No GPS location is represented. |
| FR-014 | MVP | A restaurant owner can create and update a restaurant profile for which they are the registered owner. New restaurants require administrator approval before discovery. |
| FR-015 | MVP | An owner can create and update menu items for an owned restaurant, including name, description, price, and cuisine/category data. |
| FR-016 | MVP | An owner can change an owned menu item's availability. Unavailable or inactive items cannot be ordered. |
| FR-017 | MVP | An owner can view incoming orders for owned restaurants and accept or reject a still-`PLACED` order. |
| FR-018 | MVP | An owner can advance an accepted order through `PREPARING` and `READY_FOR_PICKUP` in order, subject to authorization and valid transitions. |
| FR-019 | MVP | An administrator can approve or deactivate restaurants, manage user status and privileged roles, and manage restaurant records using restricted administrative operations. |
| FR-020 | MVP | An administrator can search/view platform orders and obtain basic counts by order status and restaurant. Operational summaries do not expose unnecessary personal data. |
| FR-021 | MVP | When an order is accepted, the Order Service requests one delivery record from the Delivery Service. Repeated requests for the same order return the existing record rather than creating duplicates. |
| FR-022 | MVP | Delivery assignment uses a finite simulated/demo partner list; an authorized administrator or delivery operator can assign a partner. |
| FR-023 | MVP | An authorized administrator or delivery operator can record pickup, delivery completion, and pre-pickup cancellation, with ordered timeline events. Delivery transitions update the corresponding order through an authenticated internal API. |
| FR-024 | MVP | A delivery timeline records assignment, pickup, delivery, and relevant cancellation events with timestamps and actor/source identifiers. |
| FR-025 | MVP | Failed downstream delivery provisioning remains visible as a recoverable order condition and can be retried idempotently; it must not silently duplicate delivery records. |
| FR-026 | Later | Customers can save favorite restaurants and receive personalized recommendations. |
| FR-027 | Later | The platform can calculate jurisdiction-specific taxes and promotions after policy and legal requirements are defined. |
| FR-028 | Later | Delivery partners can provide actual location updates. This requires a separate privacy, consent, and tracking design; it is not MVP GPS tracking. |
| FR-029 | Later | The platform can integrate a real payment processor. MVP checkout records a simulated, unpaid order only. |
| FR-030 | Later | The platform can provide customer notifications and asynchronous event delivery. No message broker or notification provider is in the MVP stack. |

## Non-functional requirements

| ID | Priority | Requirement |
|---|---|---|
| NFR-001 | MVP | Protected endpoints require a valid, unexpired JWT; authorization is role- and ownership-aware. Public registration never permits role escalation. |
| NFR-002 | MVP | All input is validated at API boundaries. IDs, quantities, pagination bounds, enum values, and monetary values are validated before persistence. |
| NFR-003 | MVP | Each service owns its database and validates its own data. Cross-service references use stable IDs; no service reads or writes another service's collections. |
| NFR-004 | MVP | APIs use consistent JSON field naming, status codes, pagination, and a stable machine-readable error envelope. |
| NFR-005 | MVP | Failures are explicit and diagnosable. Service calls have finite connection/read timeouts, bounded safe retries, and documented partial-success outcomes. |
| NFR-006 | MVP | Order creation and delivery creation are idempotent. Order totals are calculated server-side with decimal arithmetic and persisted checkout snapshots. |
| NFR-007 | MVP | Restaurant search and order history are paginated with bounded page sizes and indexes matching expected filters and sort order. |
| NFR-008 | MVP | Code is organized so services, validation, authorization, pricing, and state transitions can be unit tested; APIs and service integrations can be tested independently. |
| NFR-009 | MVP | Logs exclude passwords, JWTs, payment credentials, and full delivery addresses. Logs use correlation/request identifiers and only necessary user/order identifiers. |
| NFR-010 | MVP | Services use explicit MongoDB schema rules and indexes for uniqueness and common queries. Order snapshots preserve historical names, prices, and delivery-address details. |
| NFR-011 | MVP | Service dependencies and resilience limits are documented. Retries are bounded and restricted to transient, safe, idempotent operations. |
| NFR-012 | Later | Availability objectives, load targets, monitoring/alerting, and production backup/restore objectives are measured and specified before production deployment. No unverified performance or availability guarantee is made. |
| NFR-013 | Later | Accessibility, localization, retention schedules, and jurisdiction-specific privacy/legal requirements are reviewed before a public launch. |

## Scope and acceptance boundaries

The MVP is a demo of discovery, menu browsing, validated checkout, restaurant order processing, and simulated delivery tracking. It does not process payments, calculate tax, provide GPS tracking, guarantee eventual delivery through a broker, or claim production-scale resilience. See [architecture](architecture.md), [API contracts](api-contracts.md), and [workflows](order-workflow.md) for the detailed rules.
