# Apniko master catalogue — data and import policy

## What the master catalogue represents

The master catalogue describes products that can be listed by many shops. It is not a stock feed and it is not a price list. A product imported here does **not** become available at any shop. A shopkeeper must add it to that shop's inventory and set that shop's selling price and quantity.

The existing `shop_inventory.price` is the local selling price. Imported MRP, where present, is stored separately on the shared product as a sourced reference value. An imported MRP is never copied into a shop's selling price.

## Sources reviewed

### PackSense India / Open Food Facts

- Dataset: [PackSense India product catalogue](https://huggingface.co/datasets/PackSense/packsense-india)
- Snapshot represented by the pinned download: 3 November 2026.
- Dataset card reports 25,209 rows. It reports 21,999 product names and 17,157 brands; pack quantity fields are present on only a subset of rows.
- Licence: ODbL-1.0 for the database; individual contents are under the Database Contents Licence (DbCL-1.0).
- The PackSense CSV contains no images or image links.
- Data is user-contributed and may be incomplete, outdated, miscategorised or incorrect. The dataset card also records invalid/unverified product statuses and quality flags.

The importer therefore accepts only rows with a product name, a valid GTIN, an accepted existence status and zero open error flags. It maps available product taxonomy to Apniko's own category tree. It does not import images, nutrition values, or any source price as a local selling price. MRP is absent from this PackSense snapshot.

### Open Food Facts

- [API and data documentation](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/)
- [Licence guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)
- Database: ODbL-1.0; individual contents: DbCL-1.0; product images: CC BY-SA.
- The Open Food Facts documentation recommends bulk CSV/JSONL data downloads for importing more than a few hundred products and warns that data quality is not guaranteed.

### OpenMRP India

- [OpenMRP repository and download documentation](https://github.com/openmrp-in/openmrp)
- [Open data dump format](https://github.com/openmrp-in/openmrp/blob/main/dump/README.md)
- Data: ODbL-1.0; contributed media: CC BY-SA 4.0.
- The dump publishes separate `products.csv`, `brands.csv` and `variants.csv` tables with a manifest and SHA-256 checksums. The importer joins these tables using product and brand identifiers and preserves each product/variant source record.
- MRP is imported only when its source is one of `pack`, `brand`, `gov` or `off`. Product images are deliberately not imported in Phase 2.

### BigBasket / Kaggle

The previously discussed BigBasket dataset is **not an import source in this phase**. A verifiable reuse licence for the specific Kaggle copy was not established. Historical retailer prices must not be treated as current shop prices. Apniko does not scrape BigBasket, Amazon, JioMart or other retailers' private catalogues.

## Open-data licensing and provenance

Each imported source record is saved in `product_catalogue_source_records` with source name, source record ID, source URL, licence identifier, attribution text, source modification timestamp and any reported MRP/source. Multiple sources can therefore be deduplicated to one product without losing provenance.

ODbL-licensed source data carries attribution and share-alike obligations when a database is shared. Apniko must preserve the applicable licence notices and make the relevant ODbL-derived database available under compatible terms when it conveys a derivative database. This implementation records provenance and the source licence; it is not a substitute for a legal review of Apniko's intended commercial distribution model.

Do not add source images in this phase. Image reuse has separate licences and is handled by the dedicated product-image phase.

## Product identity and deduplication

1. GTIN-8, GTIN-12, GTIN-13 and GTIN-14 are normalized and check-digit validated.
2. A valid barcode is the primary identity key.
3. When there is no barcode, Apniko hashes normalized brand, product name, pack size and variant group into a stable identity key.
4. A matching barcode attaches another source record to the existing product rather than creating a second product.
5. A different source does not overwrite non-empty master data already curated from another source. It can fill missing fields. A re-import from the same source can refresh that source's fields.
6. Shop inventory, selling prices, stock quantities, orders and customer baskets are never modified by catalogue imports.

## India-first category tree

The starter taxonomy contains main categories and subcategories for:

- Fruits & Vegetables
- Dairy, Bakery & Eggs
- Foodgrains, Oil & Masala
- Snacks & Branded Foods
- Beverages
- Cleaning & Household
- Beauty & Hygiene
- Baby Care
- Gourmet & World Food
- Kitchen, Garden & Pets
- Eggs, Meat & Fish
- Other Essentials

The category list is seeded idempotently when the backend starts. The public category tree is available at `GET /api/catalogue/categories`; administrators can add a root category or one level of subcategory using `POST /api/catalogue/categories`.

## Download and dry-run

From the backend repository root:

```bash
python3 scripts/catalogue/import_catalogue.py download --source packsense
python3 scripts/catalogue/import_catalogue.py import \
  --source packsense \
  --file data/catalogue/source/packsense-india.csv \
  --limit 100
```

The PackSense downloader verifies both the pinned SHA-256 and the dataset's reported 25,209-row count. The import command performs local validation and prints eligible/excluded counts; without `--apply`, it does not call the API.

To download OpenMRP:

```bash
python3 scripts/catalogue/import_catalogue.py download --source openmrp
python3 scripts/catalogue/import_catalogue.py import \
  --source openmrp \
  --products data/catalogue/source/products.csv \
  --brands data/catalogue/source/brands.csv \
  --variants data/catalogue/source/variants.csv \
  --limit 100
```

## Apply to the local development database

1. Start the backend against the intended local database. Hibernate's existing `ddl-auto=update` setting creates the new catalogue columns and provenance/category tables.
2. Sign in as an active administrator and set the returned JWT in `APNIKO_ADMIN_TOKEN`.
3. Run the dry-run first and inspect the product count and exclusions.
4. Apply in small batches (default 250, maximum 500):

```bash
export APNIKO_ADMIN_TOKEN='YOUR_ADMIN_JWT'
python3 scripts/catalogue/import_catalogue.py import \
  --source packsense \
  --file data/catalogue/source/packsense-india.csv \
  --batch-size 250 \
  --apply
```

The API is admin-only: `POST /api/admin/catalogue/import`. The server independently validates the HTTPS source URL, allowed licence identifier, category/subcategory membership, barcode checksum, field lengths, quantity and MRP ranges.

Keep source files and import reports out of public issue trackers if the source data's licence or contents do not permit redistribution. The downloaded CSV is stored under `data/catalogue/source/` and should not be committed as an unreviewed snapshot.

## Operational notes

- The importer is repeatable. Re-running the same source records updates or counts unchanged entries instead of creating duplicates.
- Import errors are returned per row and capped at 100 messages per API batch.
- Rejected rows must be reviewed at source; do not weaken validation just to increase the row count.
- Imported MRP is informational and can be stale. A shopkeeper's current selling price remains independent.
- The Phase 3 product image system must separately record image attribution and image licence; this importer intentionally does not transfer image URLs.
