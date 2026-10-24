# FoodieExpress Implementation Plan

This is an independently reviewable roadmap. Phases 1 and 2 are complete. The customer identity portion of Phase 3 is implemented; its admin APIs remain planned. Restaurant, customer order, and Order-to-Delivery lifecycle functionality for Phases 4–6 is implemented within the scope stated below. The Phase 7 frontend MVP is implemented against documented public APIs. Phase 8 local live cross-service validation is complete; production/deployment validation and demo preparation remain incomplete. No commits or artificial development history are created by this plan.

## Phase 1 — Requirements and architecture documentation

- **Goals:** Agree on scope, ownership, contracts, state transitions, data model, and delivery sequence.
- **Tasks:** Complete and cross-check README, requirements, architecture, API contracts, database design, workflows, and implementation plan.
- **Expected files/components:** `README.md`, `docs/requirements.md`, `docs/architecture.md`, `docs/api-contracts.md`, `docs/database-design.md`, `docs/order-workflow.md`, `docs/implementation-plan.md`.
- **Dependencies:** None.
- **Acceptance criteria:** All requirements have IDs/priorities; endpoints and status transitions agree; service/database ownership is explicit; no unimplemented behavior is claimed as implemented.
- **Relevant tests/checks:** Markdown link check, Mermaid syntax review, endpoint/status/role consistency review, documentation claims review.
- **Git commit milestone:** One reviewable documentation milestone when explicitly requested; do not fabricate history.

## Phase 2 — Spring Boot service skeletons and MongoDB configuration

- **Status:** Complete.
- **Goals:** Establish four independently runnable Java services with consistent project structure and service-owned database configuration.
- **Tasks:** Created four independent Maven/Spring Boot applications; configured Spring Web MVC, Spring Security, Spring Data MongoDB, Bean Validation, service ports, database names/URIs, explicit-origin CORS, liveness endpoint, stateless deny-by-default security, shared error DTO/exception advice, and service startup documentation. The liveness endpoint explicitly does not check MongoDB readiness.
- **Expected files/components:** `backend/user-service/`, `backend/restaurant-service/`, `backend/order-service/`, `backend/delivery-service/`, each with its own `pom.xml`, namespaced Java application/configuration, properties, and tests; no shared cross-service database access.
- **Dependencies:** Phase 1 contracts and naming.
- **Acceptance criteria:** All four applications load independent service port/database settings; MongoDB URI and CORS origins are environment-configurable; wildcard CORS is rejected; only health is public; unknown/future routes are not accidentally exposed; no secrets or business APIs/entities/repositories were added.
- **Relevant tests:** Per-service Spring context, health JSON/error shape, protected-route security, explicit-origin/wildcard CORS, and port/database property tests. Test MongoDB URI points at an intentionally unreachable local port, confirming context tests do not require a database connection. Each packaged JAR was separately launched on its configured port with MongoDB unavailable and the live health response was verified. These are not MongoDB integration tests.
- **Git commit milestone:** Service skeleton/configuration milestone.

## Phase 3 — User Service and JWT security

- **Status:** Customer registration/login, JWT, profile, and saved-address scope implemented. Admin and internal-service endpoints remain deferred.
- **Goals:** Implement customer registration/login, profiles, addresses, JWT issuance/verification support, and protected administrative user operations.
- **Tasks completed:** Added User and Address MongoDB documents/repositories; defined user-email uniqueness and address query indexes; hash passwords with PBKDF2; enforce customer-only public registration; implement DTO validation, generic login failures, own-profile/address ownership checks, and short-lived HS256 JWT issuance/verification in all four service foundations.
- **Deferred:** Administrative list/status/role APIs, schema validation, managed key rotation, refresh/revocation, and production rate limiting. A narrowly scoped Order Service address lookup was added as a Phase 5 checkout dependency; centralized service-token issuance remains deferred.
- **Expected files/components:** User domain, DTOs, controllers, services, repositories, security configuration, MongoDB validation/index setup, tests.
- **Dependencies:** Phase 2.
- **Acceptance criteria reached for implemented scope:** Duplicate index conflicts map to a safe `409`; user-supplied roles are rejected; JWT signature, issuer, audience, and expiry are validated; users can access only their own profile/addresses; request validation is applied.
- **Tests run:** Registration normalization/password hashing/CUSTOMER assignment, duplicate-key conflict mapping, generic login failures, role input rejection, HS256 signature/claims/expiry verification and algorithm rejection, secret validation, valid/invalid protected-resource requests in all services, and context/health/security/CORS tests. MongoDB persistence/index provisioning, profile/address database ownership, and deferred admin/internal endpoints are not integration-tested or implemented.
- **Git commit milestone:** User identity/security milestone.

## Phase 4 — Restaurant Service and menu APIs

- **Status:** Restaurant Service scope implemented. Public discovery/menu APIs, role-protected owner/admin management, internal checkout quote, owner verification, and scoped pickup-location lookup are implemented. Internal authentication supports scoped Order-to-Restaurant calls.
- **Goals:** Implement restaurant registration/approval, owner-managed profiles and menus, public discovery, and internal checkout/ownership queries.
- **Tasks completed:** Added Restaurant and MenuItem MongoDB models/repositories/services/DTOs; implemented active-only public discovery/detail/menu reads, MongoDB text search, exact cuisine and availability filters, bounded pagination, deterministic name sorting, owner-controlled create/update and availability operations, and admin listing/approval/deactivation. Owner IDs are derived from the verified JWT subject. Database failures return controlled `503` errors. Index definitions and explicit local provisioning commands are documented.
- **Authentication:** Order-to-Restaurant internal calls use a dedicated P-256 ES256 key pair provisioned out of band. Order Service alone holds the private key; Restaurant Service holds the public key. Internal JWTs have fixed issuer/subject/audience, operation scope, issued/expiry claims, and a maximum two-minute lifetime. Ordinary customer HS256 JWTs cannot access `/internal/**`; the existing user-token format was not changed.
- **Deferred:** User Service role administration is not implemented, so owner/admin roles must already exist in JWTs before management routes can be used. The centralized `/internal/auth/service-token` API remains planned; scoped Order-to-User address lookup is implemented as part of Phase 5.
- **Indexes:** Restaurants use status/name/ID, owner/status, cuisine, and text indexes; menu items use restaurant/availability/name/ID and ID/restaurant indexes. Indexes are explicitly provisioned in the `food_restaurant` database; no startup migration/index creator was added.
- **Expected files/components:** Restaurant and menu DTOs, controllers, services, repositories, security rules, database validation, tests.
- **Dependencies:** Phase 2 and User Service identity/roles from Phase 3.
- **Acceptance criteria reached:** Only active restaurants are publicly visible; owners cannot update another restaurant; menu prices use Decimal128/BigDecimal and USD decimal-string API representation; availability filtering, cuisine/search filters, pagination, and deterministic sorting are implemented.
- **Remaining acceptance criteria:** Real owner/admin role provisioning remains outside the implemented services.
- **Relevant tests:** Mongo-independent context/security/validation/database-error and internal JWT/scope tests; opt-in local MongoDB integration tests cover discovery/search/cuisine/pagination, visible restaurant menus, item lookup, owner creation and ownership checks, admin approval, quote prices/quantities/availability, resource errors, and price serialization. Run with `-Drestaurant.mongo.integration=true`; these tests use a dedicated `food_restaurant_phase4_test` database.
- **Git commit milestone:** Restaurant/menu milestone.

## Phase 5 — Order Service and checkout workflow

- **Status:** Implemented. Customer checkout/history/detail/cancellation, owner listing and transitions, acceptance-triggered delivery provisioning with retry, and administrative pre-pickup cancellation are implemented.
- **Goals:** Implement checkout validation, order snapshots, pricing, customer history, and safe customer cancellation, then complete the separately specified owner/admin/delivery lifecycle.
- **Tasks completed:** Added Order documents, DTOs, repository, service/controller, scoped downstream clients, checkout validation, Decimal `BigDecimal` USD calculations, address/menu snapshots, customer-scoped retrieval, idempotent creation, and atomic cancellation from `PLACED`. Added a separate Order-to-User ES256 key pair and User Service's protected, ownership-checked address lookup. Order-to-Restaurant credentials remain separate. Downstream failures and MongoDB failures return controlled errors.
- **Tasks deferred:** Administrative order listing/summary and delivery-agent order operations remain out of scope. Restaurant-owner order listing/confirmation/rejection/preparation, delivery provisioning/retry, callbacks, delivery-owned status transitions, and pre-pickup cancellation coordination are implemented.
- **Expected files/components:** Order models/DTOs/controllers/services/repositories, pricing and transition policies, REST clients/configuration, security, tests.
- **Dependencies:** Phases 2–4.
- **Acceptance criteria reached for implemented scope:** Client-supplied prices/totals and unknown request fields are rejected; authenticated JWT subject determines order customer; address ownership and menu quote are validated before persistence; order snapshots and totals are server-generated; USD-only calculations use `BigDecimal`; idempotency is enforced by a required production unique index; customer history/detail are owner-scoped; restaurant-owner access is verified against persisted ownership; status transitions follow the documented lifecycle; provisioning retries reuse the order ID; customer cancellation is atomic and allowed only from `PLACED`; failed dependencies do not create a successful checkout or a falsely completed provisioning.
- **Acceptance criteria remaining:** Operational index provisioning and runtime verification in a deployment environment. Local live cross-service integration was completed in Phase 8 using isolated runtime databases.
- **Relevant tests:** Order context/security/token tests and opt-in MongoDB-backed tests cover context/configuration, auth, unauthorized and wrong-role access, invalid client price/quantity, price totals, address/menu snapshots, idempotency/replay conflict, missing/unavailable/malformed/non-USD quotes, dependency errors, customer and restaurant-owner isolation, lifecycle transitions, provisioning success/retry, callback idempotency, cancellation, and index presence. Integration tests can target a fresh database via `-Dorder.mongo.integration.database=<name>` and stub User, Restaurant-owner, and Delivery clients; those tests do not exercise live downstream HTTP. Separate local runtime verification passed checkout, provisioning, delivery lifecycle, callbacks, cancellation, and retry after a real Delivery Service outage.
- **Git commit milestone:** Order/checkout milestone.

## Phase 6 — Delivery Service and tracking

- **Status:** Delivery Service provisioning, assignment, lifecycle, cancellation, timeline, Order callback, Order-side owner acceptance, delivery ID association, provisioning retry, and admin pre-pickup cancellation are implemented. Restaurant-owner delivery-detail reads remain unsupported because the documented API does not require them.
- **Goals:** Implement one delivery per accepted order, demo partner assignment, delivery status/timeline, and safe order callbacks.
- **Tasks completed:** Added Delivery persistence/DTO/controller/service and a unique `orderId` constraint declaration; internal idempotent provisioning restricted to Order Service `delivery:create` ES256 token; configured demo partner selection; delivery/admin assignment, pickup/completion, admin or internal pre-pickup cancellation; customer/operator/admin reads; persist-before-callback status reporting with pending marker and same-event retry. Added Order Service's scoped callback endpoint, restaurant-owner verification and order transitions, internal pickup-location lookup, acceptance-triggered provisioning, checked delivery association, safe provisioning retries, and coordinated admin cancellation.
- **Expected files/components:** Delivery DTOs/controllers/services/repositories, REST callback client, demo partner configuration, directional internal JWT security, Order callback DTO/controller/service logic, tests.
- **Dependencies:** Phases 2 and 5.
- **Acceptance criteria reached for implemented scope:** Unique order ID prevents duplicate deliveries when the documented index is provisioned; identical create retries return existing data and conflicting payloads fail; assignment accepts only configured partners; invalid transitions fail; callbacks are persisted after events, signed with a separate Delivery-to-Order key pair, and idempotent by event ID; callback failure leaves a persisted pending marker and repeating the transition retries it.
- **Acceptance criteria remaining:** Production must explicitly provision the documented unique `orderId` and query indexes and validate them in the deployment environment. Local runtime verification covered the full checkout-to-delivery workflow and retry after a real Delivery Service outage. Restaurant-owner delivery-detail reads are not part of the current API contract.
- **Relevant tests:** Unit/context tests cover health/security, separate HS256 and ES256 token verification, tampering/expiry/scope rejection, CORS, and protected endpoints. Opt-in Delivery MongoDB tests cover unique idempotent create, conflict replay, assignment, transitions, callback retry, and ownership isolation using a fresh database selected with `-Ddelivery.mongo.integration.database=<name>`; Order MongoDB tests similarly use `-Dorder.mongo.integration.database=<name>`. These suites use MongoDB but mock cross-service HTTP. Separate live runtime verification exercised real service-to-service HTTP and callbacks; MongoDB indexes were checked in the service-owned databases.
- **Git commit milestone:** Delivery/tracking milestone.

## Phase 7 — React, TypeScript, Redux UI

- **Status:** Frontend MVP implemented. Customer, restaurant-owner, delivery-operator, and administrator workspaces call the documented services. The UI does not fabricate unsupported API data.

- **Goals:** Provide usable customer, restaurant-owner, administrator, and demo-delivery screens over the documented APIs.
- **Tasks:** Create TypeScript API clients/types; implement authentication state and protected views; implement restaurant discovery/menu and local Redux cart; checkout using saved address and idempotency key; show order/delivery states and errors; add role-based navigation without treating UI checks as authorization.
- **Expected files/components:** `frontend/` with React screens, Redux slices, typed API client, form validation, HTML/CSS, and UI tests.
- **Dependencies:** Phases 3–6 API contracts and available service APIs.
- **Acceptance criteria:** UI calls documented endpoints; cart prices are visibly non-authoritative until checkout; errors/partial provisioning are understandable; users cannot access another user's data through UI or API; no GPS or real payment claims.
- **Relevant tests:** `cd frontend && npm test && npm run build`; Redux cart, API authorization/error, and token-expiry utility tests pass. Live browser verification covered customer registration/login, public discovery, and owner-role navigation; live checkout-through-delivery was exercised through real service HTTP calls.
- **Git commit milestone:** Frontend MVP milestone.

## Phase 8 — Integration testing, security review, documentation, and demo preparation

- **Status:** Local end-to-end verification complete; production/deployment readiness remains incomplete. The live workflow used separate isolated runtime databases, and opt-in Order/Delivery MongoDB tests ran against newly verified-absent disposable databases. Existing named test databases were not modified. User Service still has no public/admin role-provisioning API.

- **Goals:** Validate end-to-end behavior and readiness within the declared demo scope.
- **Tasks:** Run integration scenarios across services; review authorization, input validation, secrets/logging, idempotency, and failure handling; update setup/API/architecture docs to match actual behavior; prepare demo data and scenario script; document known limitations.
- **Expected files/components:** Integration/security tests, updated documentation, demo instructions/data that contain no real credentials or personal data.
- **Dependencies:** Phases 2–7.
- **Acceptance criteria:** Main customer-to-delivery flow and failure recovery passed locally against isolated databases; tests pass in the documented environment; findings and limitations are recorded; production availability, payments, and GPS are not claimed.
- **Relevant tests:** Service and frontend suites, contract/integration tests, authorization tests, security review, manual failure/retry demo.
- **Git commit milestone:** MVP verification/demo milestone.

## Phase sequencing notes

Each phase should be reviewed before depending on it. Phase 5 relies on User and Restaurant contracts; Phase 6 relies on Order's internal delivery contract; Phase 7 can begin against stable contracts but needs working services for full integration. Cross-service behavior must be tested without assuming a distributed transaction. Each milestone is a future commit opportunity only; this document does not create commits.
