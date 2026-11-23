# Apniko master catalogue — source policy

## Current state: empty catalogue, one inspected Phase 2 source

The reset development catalogue is intended to contain **zero master products,
zero product photos, zero product-photo candidates, zero source records and
zero seeded product categories** until a reviewed source is deliberately
approved and imported.

Open Food Facts and related catalogue feeds are excluded. Retailer websites,
retailer mirrors, scraped pages and undocumented endpoints are not approved
catalogue sources.

## Selected Phase 2 seed — Mendeley packaged foods

The selected first seed is
[Indian Packaged Foods Nutritional Composition Dataset (2026)](https://data.mendeley.com/datasets/rhswn9z9xr/1),
Mendeley Data, DOI [10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1),
Version 1. The supplied README and the NLM Dataset Catalog identify the
dataset as CC BY 4.0 and describe 852 records across 25 variables.

The exact supplied CSV was inspected: 852 rows, 25 expected headers,
UTF-8, SHA-256
`f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303`.
The duplicate upload was byte-identical. This is an Indian packaged-food
seed only, not a complete grocery catalogue and not an image feed.

The README does not specify whether nutrition values are per serving or per
100 g. It also contains malformed numeric tokens, less-than values, repeated
product/brand identities, a questionable category label and sparse
collection-source/date metadata. See `PHASE-2-DATASET-INSPECTION.md`.

## Licence and attribution

CC BY 4.0 permits sharing and adaptation, including commercial use, provided
appropriate attribution, a link to the licence and an indication of
modifications are supplied. The Mendeley DOI and full dataset attribution
are retained in source provenance. The importer confirmation flags are
internal operator checks for licence compliance; they are not a request for
separate written permission from the dataset provider.

The licence grants only rights the licensor is entitled to grant. Any
specific embedded element with separate third-party rights must be assessed
on its own.

## Field and data-quality boundaries

- Map only source fields that are present and understood.
- Product name, brand, category and sub-category are mapped to the product
  model. Ingredients and all nutrition/source values are retained as raw
  source metadata, not published as verified facts.
- Retain the original source row ID, dataset DOI/version, CSV hash, licence
  and attribution.
- A nutritional serving size is not the retail pack size.
- Never invent GTINs, barcodes, pack sizes, variants, MRP, product photos or
  other missing facts.
- Dataset `Price_INR` is an observed source value only; it is not the current
  shop selling price or verified MRP.
- Do not convert less-than tokens, comma decimals or malformed values to
  numeric values by guessing.
- Repeated item-name + brand rows are kept separate by source row ID and
  marked for administrator review instead of being merged.
- Unclassified or questionable source taxonomy values remain visible in
  admin review; do not silently rewrite them.
- A master product does not automatically become inventory at any shop.

## Importer

`scripts/catalogue/import_catalogue.py` is the Mendeley version-1
file-based importer. It does not download the dataset. Dry-run is the
default; the exact schema and 852-row count are required. The backend source
gate accepts only the Mendeley dataset URL and CC-BY-4.0 source identifier.

Applying requires an administrator token and explicit operator confirmation
that the supplied files identify CC BY 4.0 and that Apniko's intended
commercial use complies with that licence. This is a compliance attestation,
not a separate permission request. The dataset DOI is retained as the source
reference in the existing import API contract. Source metadata is stored
privately in the catalogue source-record table.

The full local dry-run read 852 records and made no API calls or database
changes. No products have been imported; the development catalogue remains
empty.

## Product images

The Mendeley dataset is not a product-image source. Products remain
text-first with a neutral no-photo state. Do not use Open Food Facts images,
scraped retailer images, stock placeholders or generated package imagery.
Any future manufacturer/supplier photograph requires a documented usage
right for Apniko's intended use, exact product/variant matching and
administrator approval. See `PRODUCT-PHOTOS.md`.

## Resetting the local development catalogue

The backup-first reset helper is
`scripts/catalogue/reset-local-catalogue.sh`. It is a local-only operation
and must not be pointed at production or a remote database. The project owner
ran it on 2026-11-22 and verified that the existing SQL backup is present and
non-empty. The reset counters were reported as zero. Do not rerun the
destructive reset to verify this.
