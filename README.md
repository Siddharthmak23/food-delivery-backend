# FoodieExpress

FoodieExpress is a food-delivery application with four independently runnable Java/Spring Boot services and a React/TypeScript frontend. Phase 1 documents the requirements and architecture; Phase 2 established the service foundations; Phase 3 implements customer authentication, profiles, saved addresses, and shared HS256 JWT validation. Phase 4 implements Restaurant Service discovery, menu browsing, role-protected management, and scoped Order-to-Restaurant calls. Phases 5–6 implement customer checkout/history/cancellation, restaurant-owner order operations, acceptance-triggered Delivery provisioning with retry, delivery lifecycle operations, and signed status callbacks. Phase 7 includes customer, restaurant-owner, delivery-operator, and admin workspaces over documented APIs. A full checkout-to-delivery workflow and recovery scenarios have now passed local runtime verification against isolated MongoDB databases; this is not production or deployment validation. Administrative User Service role APIs, owner restaurant listing, and delivery queue listing remain unavailable.

The MVP does not include real payments or GPS tracking. Service data is owned by each service; cross-service database access is not used. There is no API Gateway.

## Technology and versions

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven, Spring Web MVC, Spring Security, Spring Data MongoDB, Jakarta Bean Validation |
| JWT | Nimbus JOSE through Spring Security; HS256 with a shared per-environment secret |
| Database | MongoDB, one logical database per service |
| Frontend | React 19, TypeScript, Vite, Redux Toolkit; Vitest and Testing Library |

The implementation environment has Oracle JDK 21.0.3 and Maven 3.10.0. Spring Boot 4.1.1 supports Java 21. Boot 4 uses `spring-boot-starter-webmvc` and Jackson 3.

## Services

| Service | Port | MongoDB database | JWT role |
|---|---:|---|---|
| User Service | 8081 | `food_user` | Issues and validates user JWTs |
| Restaurant Service | 8082 | `food_restaurant` | Validates user JWTs; restaurant and menu APIs |
| Order Service | 8083 | `food_order` | Validates user JWTs |
| Delivery Service | 8084 | `food_delivery` | Validates user JWTs |

Each service has its own Maven build, Java package, and `src/main/resources/application.properties`. Each service validates HS256 tokens with the same environment-specific secret, issuer (`foodieexpress-user-service`), and audience (`foodieexpress-api`). Restaurant discovery/menu reads are public; restaurant-owner and administrator writes require the corresponding verified JWT role.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | Service-specific port above | Overrides that service's HTTP port. |
| `MONGODB_DATABASE` | Service-specific database above | Overrides that service's logical MongoDB database. |
| `MONGODB_URI` | `mongodb://localhost:27017` | MongoDB connection string. Supply credentials/TLS options through the runtime environment, never committed configuration. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Explicit comma-separated origins; wildcard and empty configuration are rejected. |
| `JWT_SECRET` | Required for all four services | Standard Base64 encoding of cryptographically random secret bytes; minimum decoded length is 32 bytes. |
| `JWT_ISSUER` | `foodieexpress-user-service` | Expected token issuer. |
| `JWT_AUDIENCE` | `foodieexpress-api` | Expected token audience. |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M` | User Service token lifetime; positive and capped at `PT1H`. |
| `ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64` | Required by Order Service | Base64-encoded DER PKCS#8 P-256 private key. Order Service alone uses it to sign its scoped ES256 internal JWTs. |
| `ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64` | Required by Order and Restaurant Services | Base64-encoded DER X.509 P-256 public key used to verify the matching internal JWTs. Restaurant Service cannot sign them. |
| `ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64` | Required by Order Service | Separate Base64-encoded DER PKCS#8 P-256 private key for short-lived scoped address-lookup JWTs. |
| `ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64` | Required by Order and User Services | Matching Base64-encoded DER X.509 P-256 public key used to verify Order-to-User internal JWTs. User Service cannot sign them. |
| `ORDER_INTERNAL_JWT_ISSUER` | `foodieexpress-order-service` | Fixed expected issuer for Order-to-Restaurant service tokens. |
| `RESTAURANT_INTERNAL_JWT_AUDIENCE` | `foodieexpress-restaurant-service` | Fixed audience for Order-to-Restaurant service tokens. |
| `INTERNAL_JWT_TTL` | `PT1M` | Order-to-Restaurant, Order-to-User, and Order-to-Delivery token lifetime; positive and capped at `PT2M`. |
| `ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64` | Required by Order Service | Dedicated Order-to-Delivery P-256 PKCS#8 private key for short-lived provisioning/cancellation tokens. Never share with Delivery Service. |
| `ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64` | Required by Order and Delivery Services | Matching Order-to-Delivery P-256 public key used by Delivery Service to verify Order Service tokens. |
| `DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64` | Required by Delivery Service | Separate Delivery-to-Order P-256 PKCS#8 private key for status callbacks. Never share with Order Service. |
| `DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64` | Required by Delivery and Order Services | Matching Delivery-to-Order P-256 public key used by Order Service to verify callback tokens. |
| `DELIVERY_INTERNAL_JWT_ISSUER` | `foodieexpress-delivery-service` | Issuer of Delivery-to-Order callback tokens. |
| `ORDER_DELIVERY_CALLBACK_AUDIENCE` | `foodieexpress-order-service` | Audience of Delivery-to-Order callback tokens. |
| `DELIVERY_INTERNAL_JWT_AUDIENCE` | `foodieexpress-delivery-service` | Audience of Order-to-Delivery internal tokens. |
| `ORDER_SERVICE_BASE_URL` | `http://localhost:8083` | Delivery Service callback destination; use HTTPS outside local development. |
| `DELIVERY_SERVICE_BASE_URL` | `http://localhost:8084` | Order Service delivery-provisioning destination; use HTTPS outside local development. |
| `DELIVERY_DEMO_PARTNERS` | `demo-partner-01:Demo Partner 1` | Comma-separated configured `partnerId:displayName` choices for assignment. |
| `VITE_USER_API_URL` | `http://localhost:8081` | Public browser-facing User Service URL. |
| `VITE_RESTAURANT_API_URL` | `http://localhost:8082` | Public browser-facing Restaurant Service URL. |
| `VITE_ORDER_API_URL` | `http://localhost:8083` | Public browser-facing Order Service URL. |
| `VITE_DELIVERY_API_URL` | `http://localhost:8084` | Public browser-facing Delivery Service URL. |

HS256 is symmetric: every service that verifies tokens possesses material capable of signing them. This is a security tradeoff, not a claim that HS256 is inherently more secure than RS256. Protect and restrict access to the secret, and use distinct secrets for development, staging, and production. Previously issued RS256 tokens do not validate after this migration; users must log in again.

The frontend sends only user JWTs to documented public APIs. It never contains service-to-service private keys or internal service tokens. Access tokens are held in `sessionStorage` and cleared on `401` or expiry; browser role checks are navigation aids, not authorization. All services must allow the exact frontend origin with `CORS_ALLOWED_ORIGINS`. `VITE_*_API_URL` values are public URLs and must not contain credentials or secrets.

### Local credentials workflow

From the repository root, generate or reuse local JWT and internal service keys:

```sh
scripts/setup-local.sh
```

The macOS-compatible script stores local credentials outside the repository under `$HOME/.config/foodieexpress/`, with directory mode `700` and files mode `600`. It creates the user-JWT secret and separate ES256 P-256 key pairs for Order-to-Restaurant, Order-to-User, Order-to-Delivery, and Delivery-to-Order calls. Each caller receives only its directional private key; destination services receive only the corresponding public key. It prints the directory, never key material, and refuses to replace a partial or invalid existing key pair.

Use the wrapper to launch each service; it loads the local secret into that service's environment without printing it:

```sh
CORS_ALLOWED_ORIGINS=http://localhost:5173 scripts/run-service.sh user-service
CORS_ALLOWED_ORIGINS=http://localhost:5173 scripts/run-service.sh restaurant-service
CORS_ALLOWED_ORIGINS=http://localhost:5173 scripts/run-service.sh order-service
CORS_ALLOWED_ORIGINS=http://localhost:5173 scripts/run-service.sh delivery-service
```

Run each command in a separate terminal from the repository root. The wrapper injects the separate ES256 private/public key material only into the relevant service processes. Direct `mvn spring-boot:run` invocations must provide `JWT_SECRET`; Order, Restaurant, User, and Delivery Services additionally require only their respective internal key environment variables.

## Run Locally on a New Machine

Run commands from the repository root except where noted.

1. **Install prerequisites:** JDK 21, Maven 3.10.0 or newer compatible Maven, Node.js 20 or newer with npm, Git, MongoDB Community, `mongosh`, and OpenSSL.

2. **Clone the repository:** Substitute the actual GitHub owner and repository:

   ```sh
   git clone https://github.com/<owner>/<repository>.git
   cd <repository>
   java -version
   mvn -version
   ```

3. **Start/configure MongoDB:** Start a local development server at `mongodb://localhost:27017`. The local example uses an unauthenticated development MongoDB. For an authenticated server, create a least-privilege application account and provide its credential-bearing `MONGODB_URI` through the process environment. Do not store credentials in Git.

   User Service has no migration runner and has automatic index creation disabled. Initialize its indexes explicitly:

   ```sh
   mongosh "mongodb://localhost:27017/food_user"
   ```

   Then, in `mongosh`:

   ```javascript
   db.users.createIndex({ email: 1 }, { unique: true })
   db.addresses.createIndex({ userId: 1, createdAt: -1, _id: -1 })
   db.addresses.createIndex({ _id: 1, userId: 1 })
   ```

   Provision Restaurant Service indexes separately:

   ```sh
   mongosh "mongodb://localhost:27017/food_restaurant"
   ```

   Then, in `mongosh`:

   ```javascript
   db.restaurants.createIndex({ status: 1, name: 1, _id: 1 }, { name: "restaurant_status_name_idx" })
   db.restaurants.createIndex({ ownerUserId: 1, status: 1 }, { name: "restaurant_owner_status_idx" })
   db.restaurants.createIndex({ cuisines: 1 }, { name: "cuisines_idx" })
   db.restaurants.createIndex({ name: "text", description: "text" }, { name: "restaurant_search_text" })
   db.menuItems.createIndex({ restaurantId: 1, available: 1, name: 1, _id: 1 }, { name: "menu_restaurant_available_name_idx" })
   db.menuItems.createIndex({ _id: 1, restaurantId: 1 }, { name: "menu_item_restaurant_idx" })
   ```

   Restaurant text search uses MongoDB's text index over name and description, while results remain deterministically sorted by name then ID rather than relevance. Indexes are provisioned explicitly; the application does not create indexes at startup. Order Service indexes are also explicit and must be in place before production checkout. Follow the five `orders` index commands in [database-design.md](docs/database-design.md), using the `food_order` database. The unique customer/idempotency index is essential for race-safe retries. Automated Order integration tests use a dedicated configurable test database and create the indexes required by those tests; they do not provision `food_order`. Delivery indexes must be provisioned in `food_delivery` using the documented commands in `docs/database-design.md`; its unique `orderId` index is required for race-safe provisioning idempotency.

4. **Generate the development secret:** Run `scripts/setup-local.sh` from the repository root. It stores Base64-encoded random bytes, not raw text. Do not copy its contents into a source file or command history.

5. **Configure services:** Use `scripts/run-service.sh <service-name>` so services load local keys without printing them. `JWT_SECRET` is required by each service: User Service signs and verifies user tokens; Restaurant, Order, and Delivery validate them. Order Service needs its Order-to-Restaurant, Order-to-User, and Order-to-Delivery private/public key material plus the Delivery-to-Order public key. Delivery Service receives the Order-to-Delivery public key and its own Delivery-to-Order private/public keys; it never receives Order's private key. Restaurant and User Services each receive only their matching public verification key. Keep private keys out of destination services and use separate key material in each deployment environment. Vite's default origin is `http://localhost:5173`, so allow it for each backend, for example `CORS_ALLOWED_ORIGINS=http://localhost:5173 scripts/run-service.sh user-service`. A manual environment template contains placeholders only:

   ```dotenv
   JWT_SECRET=<base64-encoded-random-secret-of-at-least-32-bytes>
   ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64=<order-service-only-pkcs8-private-key>
   ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64=<matching-p256-public-key>
   ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64=<separate-order-service-only-pkcs8-private-key>
   ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64=<matching-user-service-verification-key>
   JWT_ISSUER=foodieexpress-user-service
   JWT_AUDIENCE=foodieexpress-api
   ORDER_INTERNAL_JWT_ISSUER=foodieexpress-order-service
   RESTAURANT_INTERNAL_JWT_AUDIENCE=foodieexpress-restaurant-service
   INTERNAL_JWT_TTL=PT1M
   ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64=<order-service-only-pkcs8-private-key>
   ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64=<matching-p256-public-key>
   DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64=<delivery-service-only-pkcs8-private-key>
   DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64=<matching-p256-public-key>
   DELIVERY_INTERNAL_JWT_ISSUER=foodieexpress-delivery-service
   DELIVERY_INTERNAL_JWT_AUDIENCE=foodieexpress-delivery-service
   ORDER_DELIVERY_CALLBACK_AUDIENCE=foodieexpress-order-service
   ORDER_SERVICE_BASE_URL=http://localhost:8083
   DELIVERY_DEMO_PARTNERS=demo-partner-01:Demo Partner 1
   ```

6. **Build and test backend services:** From the repository root:

   ```sh
   for service in user-service restaurant-service order-service delivery-service; do
     (cd "backend/$service" && mvn clean package) || exit 1
   done
   ```

   Run the frontend checks and create its production bundle:

   ```sh
   cd frontend
   npm ci
   npm test
   npm run build
   ```

   Context and JWT tests use ephemeral test secrets and unreachable MongoDB URIs. Restaurant Service's optional MongoDB integration tests run against a dedicated local test database and cover persisted quote pricing, item availability, and owner verification:

   ```sh
   mvn -f backend/restaurant-service/pom.xml -Drestaurant.mongo.integration=true clean package
   ```

   This command requires MongoDB at `mongodb://127.0.0.1:27017` and drops only the `restaurants` and `menuItems` collections in `food_restaurant_phase4_test`. Run an opt-in suite only when its named test database is disposable; inspect it first and do not run if it contains data to preserve.    Order Service's optional MongoDB suite defaults to `food_order_phase5_test`; override it with a fresh database name after confirming that database does not exist:

   ```sh
   mvn -f backend/order-service/pom.xml -Dorder.mongo.integration=true \
     -Dorder.mongo.integration.database=food_order_phase5_disposable_run1 clean package
   ```

   This command requires MongoDB at `mongodb://127.0.0.1:27017`. The suite creates test indexes and clears the configured database's `orders` collection before each test. Use only a fresh, disposable database name; inspect it first and never point it at development or production data. Order integration tests stub User, Restaurant, and Delivery HTTP clients; they exercise order lifecycle, ownership checks, provisioning retries, and callbacks against MongoDB but do not verify live downstream HTTP. They do not test production index provisioning.

   Delivery Service integration tests default to `food_delivery_phase6_test`; use a distinct fresh database name:

   ```sh
   mvn -f backend/delivery-service/pom.xml -Ddelivery.mongo.integration=true \
     -Ddelivery.mongo.integration.database=food_delivery_phase6_disposable_run1 clean package
   ```

   The suite requires MongoDB at `mongodb://127.0.0.1:27017`, creates test indexes, and clears the configured database's `deliveries` collection before each test. Inspect the chosen database and use only a fresh, disposable one. Callback-specific tests seed matching state to exercise callback transition/idempotency rules independently; lifecycle tests also verify owner acceptance persists the delivery association. These opt-in suites use a real MongoDB but mock cross-service HTTP; separate local runtime verification exercised the live HTTP workflow.

7. **Start services:** Start MongoDB and User Service first. Start Restaurant Service before attempting checkout through Order Service; the services do not automatically start each other. Launch each service in a separate terminal:

   ```sh
   scripts/run-service.sh user-service
   scripts/run-service.sh restaurant-service
   scripts/run-service.sh order-service
   scripts/run-service.sh delivery-service
   ```

   Override settings in the terminal running a service, e.g. `SERVER_PORT=8181 MONGODB_URI='mongodb://localhost:27017' scripts/run-service.sh user-service`.

8. **Start frontend:** In a separate terminal:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

   Vite serves `http://localhost:5173`. Set `VITE_USER_API_URL`, `VITE_RESTAURANT_API_URL`, `VITE_ORDER_API_URL`, and `VITE_DELIVERY_API_URL` before running Vite if browser-facing API origins differ from local defaults. For deployment, build with the public API URLs and serve `frontend/dist/` from an HTTPS static host whose exact origin is allowed by each service.

9. **Verify health/authentication:** In another terminal:

   ```sh
   curl -i http://localhost:8081/api/health
   curl -i http://localhost:8082/api/health
   curl -i http://localhost:8083/api/health
   curl -i http://localhost:8084/api/health
   curl -i -X POST http://localhost:8081/api/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"email":"customer@example.com","password":"replace-with-a-long-local-password","displayName":"Local Customer"}'
   curl -i -X POST http://localhost:8081/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"customer@example.com","password":"replace-with-a-long-local-password"}'
   ```

   Copy `accessToken` from the login response and call the authenticated profile endpoint:

   ```sh
   curl -i http://localhost:8081/api/users/me \
     -H 'Authorization: Bearer <access-token-from-login-response>'
   ```

### Demo accounts and role provisioning

Register customer accounts through the frontend; public registration always assigns only `CUSTOMER`. User Service does not yet implement role administration, so owner/operator/admin accounts cannot be created through the UI. For an isolated local demo only, register separate accounts first, then add the role to each exact existing user in the local `food_user` database:

```javascript
db.users.updateOne(
  { email: "owner@example.test", status: "ACTIVE" },
  { $addToSet: { roles: "RESTAURANT_OWNER" } }
)
db.users.updateOne(
  { email: "operator@example.test", status: "ACTIVE" },
  { $addToSet: { roles: "DELIVERY_OPERATOR" } }
)
db.users.updateOne(
  { email: "admin@example.test", status: "ACTIVE" },
  { $addToSet: { roles: "ADMIN" } }
)
```

Run these only after connecting to the intended **local development** database, replacing the example emails with accounts you registered. Check every result reports `matchedCount: 1`; stop if it does not. `$addToSet` preserves existing roles. Do not use direct database role edits in shared or production environments; use a trusted audited provisioning process. An owner-created restaurant begins `PENDING_APPROVAL`; an administrator must approve it through the documented protected Restaurant Service endpoint. The frontend does not include user-role provisioning or restaurant moderation.

### Troubleshooting

- **Missing JWT or internal keys:** Run `scripts/setup-local.sh`, then launch with `scripts/run-service.sh`; direct Maven runs must receive the required environment variables. Never give Restaurant or User Service either internal private key.
- **Invalid/short secret:** Use Base64 encoding of at least 32 random bytes. The services fail startup with a safe message and do not echo the secret.
- **Different service secrets:** Stop and relaunch every service using the same local secret file. A token signed with another value fails validation.
- **Invalid signature/expired token:** Log in again for a fresh token and confirm the local secret matches across services. Pre-migration RS256 tokens are incompatible.
- **MongoDB connection/index issue:** Confirm MongoDB listens at the configured URI, check credentials/TLS if enabled, and verify the User, Restaurant, and Order indexes in their respective service databases using the setup commands above and in `docs/database-design.md`.
- **Port conflict:** Stop the process using the port or set `SERVER_PORT` for that service, then use the corresponding URL.
- **Missing dependencies/wrong Java:** Check `java -version` and `mvn -version` for JDK 21; run `mvn clean package` inside the affected service to resolve Maven dependencies.

## Health

| Service | Endpoint |
|---|---|
| User Service | `http://localhost:8081/api/health` |
| Restaurant Service | `http://localhost:8082/api/health` |
| Order Service | `http://localhost:8083/api/health` |
| Delivery Service | `http://localhost:8084/api/health` |

Each liveness endpoint returns a service name, `"status":"UP"`, and `"databaseStatus":"NOT_CHECKED"`. This is not a MongoDB readiness check; database-dependent operations fail explicitly when the data store is unavailable.

## Implemented APIs

### Frontend workspaces

The public Discover view searches and pages through active restaurants and menus. Customers can register/sign in, save/edit/remove addresses, keep a single-restaurant Redux cart, submit server-priced checkout with a stable idempotency key, view order history/details and delivery timeline, and cancel only while an order is `PLACED`. Restaurant owners can create/update a restaurant, add/edit menu items, change item availability where visible, list a known restaurant's orders, and accept/reject/advance preparation. Delivery operators and administrators can look up deliveries by order or delivery ID, assign a configured demo partner, and record pickup/completion. Administrators can also retry delivery provisioning or request coordinated pre-pickup cancellation.

The APIs have no owner restaurant-list endpoint, delivery-operator assigned-queue endpoint, or admin user/role provisioning endpoint. Owner and delivery views therefore require known IDs; they do not display fake lists. Pending restaurants are not publicly discoverable, which limits menu editing/listing until approval. UI role checks only protect navigation; services remain the authorization boundary.

`POST /api/auth/register` creates a CUSTOMER account, normalizes email, hashes the password, and rejects client-supplied roles. `POST /api/auth/login` returns a short-lived HS256 JWT. `GET`/`PUT` `/api/users/me` and saved-address CRUD use the verified token subject to scope operations to that user. Password hashes are never part of API response DTOs.

Address listing supports zero-based `page` (default `0`), `size` (default `20`, maximum `100`), and `sort=createdAt,asc|desc` (default `createdAt,desc`). The documented admin user endpoints and centralized internal service-token flow are not implemented.

Restaurant Service implements public `GET /api/restaurants`, `GET /api/restaurants/{id}`, `GET /api/restaurants/{id}/menu`, and `GET /api/menu-items/{id}`. Discovery supports `q`, exact `cuisine`, zero-based bounded pagination, and deterministic `sort=name,asc|desc`. Only active restaurants are visible. Owner-role requests can register restaurants (initially `PENDING_APPROVAL`), update owned restaurants, create/update menu items, and change item availability. Admin-role requests can list restaurants and approve, reject, or deactivate them. New menu prices must use INR, are stored as MongoDB Decimal128, and are returned as two-decimal strings. Existing USD menu records are not converted or relabelled; they remain visibly marked as legacy and must be explicitly repriced in the Restaurant Studio before checkout. The current User Service does not expose role assignment, so these write routes require a JWT whose owner/admin role has been provisioned outside its public registration API.

Order-to-Restaurant internal calls use short-lived ES256 JWTs, signed with a dedicated Order Service private key and verified with the corresponding Restaurant Service public key. Tokens require the Order Service issuer/subject, Restaurant Service audience, issued/expiry times within two minutes, and a fixed operation scope. Order-to-User address lookup uses a second separate key pair, User Service audience and `user:address:read` scope; User Service checks the stored address belongs to the requested active user. Both key pairs are separate from `JWT_SECRET`; ordinary user JWTs cannot authenticate to `/internal/**`. Restaurant internal endpoints include `POST /internal/restaurants/{id}/menu-quote` (`menu:read`), `GET /internal/restaurants/{restaurantId}/owners/{userId}` (`restaurant:ownership:read`), and `GET /internal/restaurants/{id}/delivery-details` (`delivery:provision`). User Service exposes `GET /internal/users/{userId}/addresses/{addressId}` only to the scoped Order Service caller. Delivery Service accepts internal `POST /api/deliveries` provisioning and internal `PATCH /api/deliveries/{id}/cancel` only from the Order Service's separate ES256 identity/scopes, and reports status to Order Service using an independently keyed callback token. Centralized service-token issuance is not implemented.

Order Service implements customer checkout/history/detail/cancellation, `GET /api/orders/restaurant/{restaurantId}` for a verified restaurant owner, `PATCH /api/orders/{id}/restaurant-status`, `POST /api/orders/{id}/delivery-provisioning/retry` for administrators, and `PATCH /api/admin/orders/{id}/cancel`. Checkout accepts a restaurant ID, 1–50 unique menu item IDs with quantities from 1–100, an owned saved-address ID, and a required customer-scoped `Idempotency-Key`. It fetches authoritative address/menu information, accepts INR quotes only, calculates `BigDecimal` totals with no delivery fee or discount, stores item/address snapshots, and creates orders as `PLACED` and `SIMULATED_UNPAID`. Replaying the same key and payload returns the original record; changed payload under that key is a `409`. Existing USD orders remain unchanged and display as legacy USD; no automatic conversion or database rewrite is performed. Owners are verified by Restaurant Service against stored ownership; acceptance persists `CONFIRMED` before provisioning, and the order ID is reused as the Delivery Service idempotency key. `deliveryId` is saved only after validating the provisioning response; retry-required outcomes remain visible and `READY_FOR_PICKUP` is blocked until provisioning completes. The internal `POST /internal/orders/{id}/delivery-status` callback is restricted to a Delivery Service ES256 token with the exact callback scope and matching `deliveryId`/state. Restaurant-owner delivery reads are not exposed by the current contract. Payment remains simulated; no external INR payment provider is configured.

## Tests and limitations

Run `mvn clean package` independently in each `backend/<service>` directory. User Service tests cover request normalization, password hashing, role-input rejection, duplicate-key exception mapping, generic login failures, HS256 issuance, signature/expiry/claim/algorithm rejection, and protected paths. Restaurant Service context tests exercise security, validation, and controlled database errors without MongoDB; opt-in integration tests exercise persisted discovery, menu filtering, ownership, approval, and the scoped delivery-details endpoint against the dedicated test database. Order Service context tests verify configuration, authorization, token behavior, service-client scopes/idempotency, price rejection, pagination bounds, and explicit MongoDB failure behavior. Its opt-in MongoDB tests cover snapshots/pricing, idempotency, owner isolation and transitions, provisioning/retries, customer isolation, cancellation callbacks, and Mongo indexes using stub User/Restaurant/Delivery clients. Delivery Service context tests cover endpoint security, HS256/ES256 trust separation, and token rejection. Its opt-in MongoDB tests cover provisioning idempotency, conflicting replays, assignment/lifecycle, callback retry, and customer isolation. These suites use dedicated databases and do not exercise live cross-service HTTP. Restaurant, Order, and Delivery production indexes require documented manual provisioning.

Known limitations include no MongoDB migration runner/schema validation, no key rotation or token revocation/refresh, no rate limiting, no production TLS configuration, no centralized internal service-token issuer, and no User Service role-administration API. Order's MongoDB integration suite stubs downstream clients and does not exercise live HTTP/token exchange. Restaurant-owner delivery reads, owner restaurant listing, and operator assigned-delivery queue listing are not supported by the documented APIs. Production MongoDB indexes must be provisioned manually; local development indexes are documented in setup step 3. Restaurant text-search matching/index behavior is MongoDB text search, not relevance-ranked search. Frontend unit tests mock browser API responses; live browser verification covered customer registration/login, discovery, and role-specific owner navigation, while checkout-to-delivery was verified through real service HTTP calls.

## Documentation and roadmap

Phase 3 implementation status and remaining scope are tracked in [docs/implementation-plan.md](docs/implementation-plan.md). Requirements, API contracts, data design, and architecture are recorded in [docs/requirements.md](docs/requirements.md), [docs/api-contracts.md](docs/api-contracts.md), [docs/database-design.md](docs/database-design.md), and [docs/architecture.md](docs/architecture.md).
