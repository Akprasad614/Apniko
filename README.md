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

Apniko's development catalogue is intentionally empty until a reviewed import
is explicitly approved. Open Food Facts and related catalogue feeds are
excluded.

Phase 2 uses only the inspected *Indian Packaged Foods Nutritional Composition
Dataset (2026)*, Mendeley Data, Version 1, DOI
[10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1), identified as
CC BY 4.0. Its 852-row CSV and README have been inspected, and a dry-run of
the Mendeley-only importer completed without API calls or database changes.
The dataset is a packaged-food seed, not a complete grocery catalogue and
contains no product photographs, GTINs or retail pack sizes. Its licence does
not grant rights to media that is not included in the dataset.

Phase 3 is a separate authorised product-photo intake and admin-review
workflow. Use only manufacturer/brand/authorised distributor submissions,
participating supplier/shopkeeper submissions with a documented usage grant,
or individual openly licensed images whose own licence permits Apniko's use.
Exact product identity and image rights must be checked independently.

Do not add sample products, synthetic product photographs, guessed barcodes,
pack sizes, live prices, ratings or stock. Shopkeepers remain responsible for
shop-specific prices and stock. See `CATALOGUE-DATA-SOURCES.md`,
`PRODUCT-PHOTOS.md` and `PHASE-3-PRODUCT-PHOTOGRAPHY.md` before changing
catalogue intake or product media.

## Project rights

See `COPYRIGHT.md` and `OWNERSHIP.md`. Third-party dependencies and assets
remain subject to their own licences.
