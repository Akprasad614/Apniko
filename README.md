# Apniko

Apniko is a neighbourhood-commerce platform that helps independent grocery
shops serve nearby customers through a shared digital storefront. Customers
discover local shops and products; shopkeepers manage their own catalogue,
prices, stock and orders; delivery partners handle assigned deliveries.

## Product catalogue policy

**Apniko is product-information-only. Product photographs and product-image
features are not part of the product.** Customer and shopkeeper catalogue
surfaces display text and structured product facts only.

The product master may store verified product name, brand, category,
subcategory, variant, GTIN/barcode, retail pack size/net quantity and sourced
MRP. Unknown facts remain empty. A shopkeeper separately sets that shop's
selling price, stock, availability and low-stock threshold. Importing a master
product never creates shop inventory.

## Data-source policy

- Open Food Facts / Open Facts data and image integrations are excluded.
- BigBasket scraping, copied retailer catalogues and unlicensed retailer
  content are excluded.
- Do not add unapproved Kaggle dumps or undocumented endpoints.
- The inspected Mendeley Indian Packaged Foods Nutritional Composition
  Dataset (2026), Version 1, DOI
  [10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1), is retained
  only as a source-attributed reference candidate. It contains 852 rows but
  has no verified retail GTIN/barcode or retail pack-size columns. All rows
  therefore remain in admin review and cannot be published as sellable
  retail variants.
- Its `Price_INR`, serving-size and nutrition fields are not current shop
  price, verified MRP, retail pack size or validated customer-facing
  nutrition facts.

The importer is dry-run by default, preserves source provenance, pins the
inspected CSV SHA-256 and requires explicit licence/commercial-use
attestations plus an administrator token before any apply. Do not invent
products, barcodes, variants, sizes, MRP, shop prices, stock or nutrition data.

## Technology

- Backend: Java 21, Spring Boot, Spring Security, JPA, MySQL
- Frontend: React 19, TypeScript, Vite 8
- API documentation and testing: OpenAPI / Swagger UI, Postman
- Frontend quality checks: ESLint, TypeScript, Vitest
- Backend quality checks: Maven verification, JUnit, JaCoCo

## Repository layout

- `src/` — Spring Boot backend
- `frontend/` — React application
- `postman/` — API and end-to-end test collections
- `scripts/` — local verification and isolated test helpers
- `.github/workflows/` — automated CI and security workflows

## Local development

See `PHASE10-README.md` for backend, Swagger and isolated database workflow.
See `frontend/README.md` for frontend setup and checks.
See `PHASE-0-BASELINE.md` for the verified project foundation.

## Current catalogue phase status

The Mendeley source has been inspected and its importer/tests are implemented.
No product rows have been applied to a database in this phase. An approved,
complete retail product-data source is still required before Apniko can
populate a large sellable Indian grocery catalogue.

Product-image code has been removed from the text-only application branch.
The guarded SQL script in `scripts/catalogue/remove-legacy-product-photo-tables.sql`
can remove the obsolete, empty legacy photo tables after a database backup;
it deliberately stops if either table contains records and has not been run
against a live database.

## Project rights

See `COPYRIGHT.md` and `OWNERSHIP.md`. Third-party dependencies and assets
remain subject to their own licences.
