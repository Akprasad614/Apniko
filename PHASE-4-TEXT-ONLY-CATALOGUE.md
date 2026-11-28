# Phase 4 — Text-only Catalogue Architecture

## Product decision

Apniko is a product-information-only marketplace. No product photographs,
image URLs, product-image uploads, placeholders, generated pack art, image
galleries, photo attribution or photo moderation are used in any role.

The Apniko brand mark and ordinary interface icons are separate from product
media. Product cards show text facts only.

## Removed from the application branch

- Product-photo admin controller, service, entities, repositories, DTOs and tests.
- Product photo review status and review history.
- Photo-intake CSV importer, example manifest and tests.
- Frontend photo components, credits, fallbacks, customer photo presentation
  and admin photo-review interface.
- Shopkeeper catalogue and inventory product-photo markup and styles.
- Image properties from product, inventory and cart API response models.
- Product-to-photo JPA relationship and repository photo joins.

## Text-only product surfaces

The master product and API responses expose product identity and information
such as name, brand, category, subcategory, barcode, retail pack size, MRP
and source context where available. Customer cart, shopkeeper inventory and
catalogue picker surfaces use those facts without image URL fields.

Shopkeepers retain separate control over each shop's price, stock, listing
availability and low-stock threshold.

## Database cleanup

Removing JPA entities does not make Hibernate `ddl-auto=update` drop old
tables. A guarded SQL script is included at
`scripts/catalogue/remove-legacy-product-photo-tables.sql`. It:
- Refuses to run against a production-looking database name.
- Counts rows in the legacy photo tables first.
- Refuses to drop either table if data exists.
- Drops only the two obsolete photo tables when they are empty.

This SQL has **not** been run against a database in this task. Back up the
target development database before running it. A production migration
baseline is still a separate Phase 13 release prerequisite.

## Verification

`scripts/verify-phase4-text-only.sh` checks for removed image components and
forbidden image fields in backend, frontend and catalogue code. Backend
Maven tests and frontend lint/typecheck/Vitest/build are required before
this phase can be called code-complete.
