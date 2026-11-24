# Apniko master catalogue — source policy

## Product-information-only rule

The catalogue contains structured product information only. Product photographs,
image URLs, image upload, image moderation, product-image storage and image
source integrations are out of scope and must not be reintroduced.

## Excluded sources

- Open Food Facts / Open Facts and derivatives such as PackSense datasets based on it.
- BigBasket scraping, copied retailer listings, retailer mirrors and undocumented endpoints.
- Unapproved Kaggle datasets, scraped pages, fabricated or synthetic catalogues.

A dataset is not approved merely because it is publicly downloadable. Its
provenance, licence, allowed commercial use, attribution obligations and field
semantics must be checked before integration.

## Inspected reference dataset

The user-provided source is the *Indian Packaged Foods Nutritional Composition
Dataset (2026)*, Mendeley Data, Version 1, DOI
[10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1). Its supplied
README identifies CC BY 4.0. The inspected CSV contains 852 rows and 25 columns,
with SHA-256:

`f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303`

This is a packaged-food nutrition/reference dataset, **not a complete retail
SKU catalogue**. It has no verified GTIN/barcode or retail pack-size field.
The README does not define the nutrition basis. Its row-level source and date
metadata are mostly absent. It must not supply current shop prices, verified
MRP, retail sizes or customer-facing nutrition facts.

The importer retains the raw source row and attribution privately. All 852
rows are marked `NEEDS_REVIEW` as reference candidates. They cannot be added
to a shop or shown to customers until an independently approved source
provides the exact retail variant identity.

## Data mapping and non-inference rules

- Map only item name, brand, category and subcategory from the source.
- Preserve the source row ID, DOI, version, CSV hash, licence, attribution and
  raw values in private source provenance.
- Never invent GTINs, barcodes, retail pack sizes, net quantities, variants,
  MRP, current shop price, stock, availability or nutrition facts.
- `Serving_Size_g` is not a retail pack size or net quantity.
- `Price_INR` is a source observation only, not current shop price or verified MRP.
- Preserve less-than tokens and malformed numeric values verbatim; never
  coerce or silently repair them.
- Keep duplicate item/brand source rows distinct until the sellable variant
  is independently identified.
- Do not silently correct source category labels. Flag them for review.
- A master product does not automatically become inventory at any shop.

## Importer safeguards

`scripts/catalogue/import_catalogue.py` is a file-based Mendeley version-1
importer. It never downloads the dataset. Dry-run is the default. It validates
the exact schema and 852-row count, preserves source provenance and flags all
rows for review. Apply additionally requires:
- The exact inspected CSV SHA-256.
- Explicit operator confirmation of CC BY 4.0 reuse and commercial-use scope.
- An administrator JWT.
- A local/staging HTTPS API endpoint (HTTP localhost is allowed for development).

No product import or database mutation has been performed as part of the
Phase 3–4 code changes.

## Future source admission

A future retail source must have a documented source owner, commercial-use
rights, stable source identifiers, exact sellable-variant data (including
barcode and printed pack size where relevant), category mapping, field-level
provenance and an update policy. The source must be explicitly approved before
an adapter or import is added.

## Local catalogue reset

The backup-first helper is
`scripts/catalogue/reset-local-catalogue.sh`. It is local-only. The project
owner ran it on 2026-11-22 and verified the backup and zero counters. Do not
rerun the destructive reset simply to re-verify it.
