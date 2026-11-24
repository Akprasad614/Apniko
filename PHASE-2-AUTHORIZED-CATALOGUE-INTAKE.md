# Historical catalogue data-intake milestone

This file records the original engineering milestone that introduced the
Mendeley packaged-food dataset importer. The current product roadmap renumbers
this work under **Phase 3 — Authentic Indian Grocery Product Catalogue**.

The selected Mendeley dataset is a reference source only. It contains 852
records and 25 variables but has no verified retail GTIN/barcode or retail
pack-size field. Its nutrition basis is unspecified and its source/date
metadata is sparse. It cannot by itself create sellable retail SKU records.

The importer validates the original CSV schema and count, preserves raw
provenance, avoids mapping its observed price to shop price or MRP, and does
not infer barcodes or pack sizes. The revised importer holds **all 852 rows**
in `NEEDS_REVIEW` pending independent retail-variant verification, and its
apply mode is pinned to the inspected CSV SHA-256.

See:
- `PHASE-3-AUTHENTIC-PRODUCT-CATALOGUE.md`
- `PHASE-2-DATASET-INSPECTION.md`
- `CATALOGUE-DATA-SOURCES.md`

No database import is performed by this documentation or code change.
