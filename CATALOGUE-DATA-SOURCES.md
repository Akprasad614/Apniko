# Apniko master catalogue — source policy

## Current state: empty and no active source

The reset development catalogue is intended to contain **zero master products,
zero product photos, zero product-photo candidates, zero source records and
zero seeded product categories** until a reviewed source is deliberately
approved and imported.

Open Food Facts and its related catalogues are excluded. BigBasket is also
excluded; do not use its website, product listings, Kaggle copy, undocumented
APIs or product images as an Apniko source. No external dataset is currently
approved or imported.

## Proposed Phase 2 seed dataset — not yet activated

The proposed first data source for Phase 2 is
[Indian Packaged Foods Nutritional Composition Dataset (2026)](https://data.mendeley.com/datasets/rhswn9z9xr/1),
Mendeley Data, DOI [10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1),
Version 1.

The repository landing page identifies a CC BY 4.0 licence and describes
852 packaged-food records. This is a candidate packaged-food seed, not a
complete grocery catalogue and not an image feed. Before implementation, the
actual downloaded CSV and README must be inspected to confirm the exact
headers, row count, data types, provenance notes, attribution requirements
and any content excluded from the dataset licence. Preserve the source
citation and record all normalization or other changes. Do not imply that
Mendeley or the dataset contributors endorse Apniko.

No Phase 2 import has been applied. Until the source files have been inspected
and a tested importer is approved, the dataset must remain outside the
application database.

## Field and data-quality boundaries

- Import only source fields that are present and understood in the inspected
  file. Retain source row identifiers and dataset/version provenance where
  available.
- Do not invent GTINs, barcodes, pack sizes, variants, product photographs,
  MRP, current shop prices, ratings or stock.
- A nutritional serving size is not automatically the retail pack size.
- Any dataset price is not a current shop-specific selling price or a verified
  current MRP. Shopkeepers set their own selling price and stock.
- Do not turn missing data into guesses. Send ambiguous or low-quality rows
  to admin review rather than silently promoting them.
- A master product does not automatically become inventory at any shop.

## Importer status

The existing BigBasket-specific CLI importer and backend source validation
are legacy implementation, not an approved source path. Do not run that
importer or submit BigBasket records. Phase 2 must replace the source-specific
mapping and validation with a Mendeley-file importer only after the actual
CSV and README have been inspected. The new importer must default to dry-run,
validate and deduplicate rows, retain attribution/provenance, and require
admin review before customer visibility.

## Product images

The proposed Mendeley dataset is not a product-image source. Products remain
text-first and use the neutral no-photo state. No Open Food Facts or BigBasket
images, retailer scraping, stock placeholders or generated package imagery
may be used. Any future manufacturer/supplier photograph requires separate
written rights for Apniko's intended use, exact product/variant matching and
administrator approval. See `PRODUCT-PHOTOS.md`.

## Resetting the local development catalogue

The backup-first reset helper is
`scripts/catalogue/reset-local-catalogue.sh`. It is a local-only operation
and must not be pointed at production or a remote database. The project owner
ran it on 2026-11-22 and reported a full SQL backup plus zero catalogue/order
verification counts. Do not rerun the destructive reset merely to verify this
record; confirm the existing backup file instead.
