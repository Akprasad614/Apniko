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
See `PHASE-0-BASELINE.md` for the verified project foundation and Phase 0
acceptance boundaries.

## Catalogue and product media

Apniko starts from an empty catalogue. Open Food Facts and its related
catalogues are not permitted sources. BigBasket is the planned initial dataset
only if written reuse permission covering Apniko's intended use is verified.
See `CATALOGUE-DATA-SOURCES.md` and `PRODUCT-PHOTOS.md` before importing
catalogue data or submitting product photographs.

## Project rights

See `COPYRIGHT.md` and `OWNERSHIP.md`. Third-party dependencies and assets
remain subject to their own licences.
