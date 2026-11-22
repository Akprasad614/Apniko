# Apniko

Apniko is a neighbourhood-commerce platform being built to help independent
grocery shops serve nearby customers through a shared digital storefront.
Shopkeepers manage their catalogue, inventory, and incoming orders; customers
discover local shops and order everyday essentials.

## Technology

- Backend: Java 21, Spring Boot, Spring Security, JPA, MySQL
- Frontend: React 19, TypeScript, Vite 8
- API documentation and testing: OpenAPI / Swagger UI, Postman
- Frontend quality checks: ESLint, TypeScript, Vitest
- Backend quality checks: Maven verification, JUnit, JaCoCo

## Repository layout

- `src/` — Spring Boot backend
- `frontend/` — React customer application
- `postman/` — API and end-to-end test collections
- `scripts/` — local verification and isolated test helpers
- `.github/workflows/` — automated CI and security workflows

## Local development

See `PHASE10-README.md` for the backend, Swagger, and isolated database
workflow. See `frontend/README.md` for frontend setup and checks.
See `PHASE-0-BASELINE.md` for the verified project foundation and phase
acceptance boundaries.

## Catalogue and product media

Apniko's development catalogue has been reset to an empty state. Open Food
Facts and BigBasket are excluded. No external product dataset is currently
approved or imported.

The Indian Packaged Foods Nutritional Composition Dataset (2026) on Mendeley
Data is the proposed Phase 2 seed dataset only. Its actual CSV, README,
field-level provenance and licence scope must be inspected before an importer
is implemented or any row is applied. It is a packaged-food dataset, not a
complete grocery catalogue, and it is not a product-image source.

Do not add sample products, synthetic product photographs, guessed barcodes,
pack sizes, live prices, ratings or stock. Shopkeepers remain responsible for
shop-specific prices and stock. See `CATALOGUE-DATA-SOURCES.md` and
`PRODUCT-PHOTOS.md` before changing catalogue intake or product media.

## Project rights

See `COPYRIGHT.md` and `OWNERSHIP.md`. Third-party dependencies and assets
remain subject to their own licences.
