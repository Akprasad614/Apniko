# Apniko retail catalogue — licensing and import handoff

Apniko remains text-only. Product images and Open Food Facts integrations are outside the approved product scope.

## Rights required

Before any commercial retail import, obtain written rights from the actual data owner/licensor specifically permitting commercial use in Apniko and the retention, normalization, display and redistribution of the exact fields involved. Clarify territory, term, updates, attribution, revocation, derived records and termination rights. A generic dataset licence or public availability is not proof of every right needed for a retail marketplace.

Do not scrape BigBasket or any retailer. Do not use copied listings, undocumented APIs, unreviewed Kaggle dumps, unlicensed images or data with ambiguous permissions.

## Provenance and validation

Retain source owner, source name and URL, retrieval date, exact source version/file SHA-256, licence ID and text, signed permission reference, permitted fields, territory/term, transformations and reviewer. Independently validate GTIN checksum/uniqueness, product/brand identity, retail pack size, category mapping, and MRP source/date. Nutritional serving size is not retail pack size; a source price is not necessarily MRP or current shop price. Never infer missing values.

## Controlled import

1. Preserve the original source and hash it.
2. Run the supported importer in dry-run mode.
3. Review duplicates, mismatched variants, row-level rejects, provenance and unsupported claims.
4. Match configured source identity to written permissions.
5. Obtain administrator/second-person approval and retain the dry-run report.
6. Apply only approved master product records. Never generate shop inventory, selling price or stock from master catalogue import.
7. Reconcile row counts and retain the source hash and import report.

The inspected Mendeley candidate is not an approved sellable retail catalogue; it lacks verified retail GTIN/barcode and pack-size columns. Keep those rows in admin review only.
