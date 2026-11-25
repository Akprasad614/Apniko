# Apniko

Apniko is a neighbourhood grocery-commerce platform that connects customers with independent shops nearby. Customers browse local stores and place orders; shopkeepers manage their own products, prices, stock and fulfilment; delivery partners handle assigned deliveries.

## Product capabilities

- **Customer:** nearby-shop discovery, product and shop search, category browsing, cart, checkout, saved addresses, order history and status tracking.
- **Shopkeeper:** shop and catalogue management, shop-specific pricing and stock, order processing, inventory movements, sales analytics and demand-based replenishment guidance.
- **Delivery partner:** assigned deliveries, acceptance, pickup and delivery status updates.
- **Administrator:** user and shop operations, catalogue review, platform analytics and Khatha oversight.
- **Payments:** Cash on Delivery and optional Razorpay checkout, with server-side verification and webhook handling.
- **Apniko Khatha:** customer credit accounts, limits, ledger entries, repayments, reversals and audit records.

## Technology

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Database | MySQL 8.x |
| Frontend | React 19, TypeScript, React Router, Vite 8 |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Tests | JUnit, Vitest, Playwright |
| Build and quality | Maven, ESLint, TypeScript |
| Deployment | Docker, Docker Compose, Nginx and Caddy |

## Repository layout

- `src/` — Spring Boot application, domain logic and backend tests
- `frontend/` — React application, frontend tests and browser acceptance tests
- `postman/` — API collections, test environments and OpenAPI snapshot
- `scripts/` — verification, catalogue import and database operations
- `.github/workflows/` — CI, security analysis and release-readiness workflows
- `compose.yaml`, `Dockerfile`, `Caddyfile` — container deployment configuration

## Local development

### Prerequisites

- JDK 21
- MySQL 8.x
- Node.js 22.13+ or 24+
- npm 10.8+

### Backend

Create a local development database named `localcart`. Configure the connection and required secrets in your shell; do not commit them.

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/localcart?allowPublicKeyRetrieval=true&useSSL=false'
export SPRING_DATASOURCE_USERNAME='root'
export DB_PASSWORD='your-local-mysql-password'
export JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"

./mvnw spring-boot:run
```

Use a dedicated local MySQL account rather than root where possible. The local development profile may update its development schema; the production profile is configured with Hibernate `validate` and must not be changed to automatic schema updates.

### Frontend

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The Vite development server proxies `/api` requests to the backend on port 8080. See [frontend/README.md](frontend/README.md) for frontend details.

## Verification

Run backend checks from the repository root:

```bash
./mvnw -B -ntp verify
```

Run frontend checks from `frontend/`:

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

The Playwright browser suite is available through `npm run test:e2e` after installing its pinned tooling and browser dependencies using `bash scripts/install-phase7-tooling.sh`.

## Catalogue and data policy

Apniko uses a **text-only product catalogue**. Product photographs, product-image uploads and Open Food Facts integrations are outside the product scope.

Master product records contain only verified product facts. Shopkeepers separately manage each shop's selling price, stock, availability and low-stock threshold. A master product import must never create shop inventory.

Do not scrape retailer catalogues or import data without documented rights for the intended commercial use. All catalogue imports must retain source provenance and begin in dry-run/review mode. See [CATALOGUE-DATA-SOURCES.md](CATALOGUE-DATA-SOURCES.md), [CATALOGUE-MODERATION.md](CATALOGUE-MODERATION.md) and [RETAIL-CATALOGUE-LICENSING-HANDOFF.md](RETAIL-CATALOGUE-LICENSING-HANDOFF.md).

## Security and operations

- Never commit populated environment files, database passwords, JWT secrets, payment credentials, webhook secrets or admin tokens.
- Never expose MySQL or the Spring Boot backend directly to the public internet.
- Production browser authentication uses secure HttpOnly cookies and CSRF protection.
- Production database access uses TLS verification and least-privilege credentials.
- Production schema changes require reviewed, versioned migrations. Hibernate remains in `validate` mode.
- Backups must be encrypted, stored off-site and periodically restored into an isolated environment.

See [SECURITY.md](SECURITY.md), [OPERATIONS-RUNBOOK.md](OPERATIONS-RUNBOOK.md), [PHASE14-STAGING-READINESS.md](PHASE14-STAGING-READINESS.md) and [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md).

## Release status

Repository implementation and automated integration checks are in place through Phase 14 preparation. **Apniko has not yet passed live staging acceptance and is not approved for production deployment.** The actual database schema baseline, staging deployment, Razorpay sandbox acceptance, Khatha reconciliation, mobile GPS verification, branch protection, external monitoring, off-site backup restore drill and catalogue commercial rights must be completed before production release.

The current release roadmap ends with Phase 15: production deployment and public launch. Advanced features outside the launch scope are deferred.
