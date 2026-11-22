# Apniko master catalogue — BigBasket dataset intake

## Empty-by-default policy

A new Apniko environment starts with **zero master products, zero product photos, zero product-photo candidates, zero catalogue source records and zero seeded product categories**. The product, catalogue, inventory and photo schemas remain available, but they contain no catalogue content until an authorised import is deliberately run.

## Sole planned dataset: BigBasket Entire Product List

The planned reference dataset is [BigBasket Entire Product List (~28K datapoints)](https://www.kaggle.com/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints).

The commonly circulated CSV schema contains fields such as:

- product
- category
- sub_category
- brand
- type
- description
- sale_price, market_price and rating

The current Apniko importer intentionally maps only product name, brand, category, subcategory, type/variant label and description. It does **not** import retailer sale prices, market prices, ratings, image URLs or guessed barcodes. It does not infer pack sizes from product titles. The reference CSV does not provide a verified GTIN or an authorised product-image feed.

## Permission gate — required before applying data

The fact that a CSV is available on Kaggle does not establish that BigBasket's underlying catalogue data is licensed for reproduction or commercial reuse. The Apache 2.0 label shown on Kaggle notebook pages applies to the notebook code; it is not evidence of a licence for the underlying BigBasket dataset.

BigBasket's published terms restrict copying, collection and commercial/derivative use of product listings and website content without express written consent. See [BigBasket terms and conditions](https://www.bigbasket.com/fp/web/terms-and-conditions/).

Therefore:

1. Apniko does not scrape BigBasket, call undocumented BigBasket APIs, download product images, or crawl product pages.
2. A CSV must be supplied locally by the project owner from a source that has explicitly granted the required reuse rights.
3. Before --apply, the operator must provide the written permission/licence reference and explicitly confirm that it covers product catalogue reuse in Apniko.
4. The backend independently requires the source name 'BigBasket authorised dataset', the permission classification 'BIGBASKET-WRITTEN-PERMISSION', and a permission reference in the source attribution.
5. If the permission cannot be verified, keep the CSV in dry-run mode and do not import it.

## Product identity and prices

The reference dataset does not contain verified GTINs. Apniko uses a stable identity derived from source product name, brand, category and variant/type fields when the barcode is absent. Such records are provisional identities; a shopkeeper still needs to verify exact pack/variant before creating a shop listing.

Historical BigBasket sale/market price values are deliberately ignored. They are not a shopkeeper's live selling price and are not assumed to be current MRP. Each shopkeeper sets their own selling price, quantity, availability and low-stock threshold.

A master product does not automatically become inventory at any shop.

## Images

The BigBasket product-list CSV is not treated as an image feed. New products import with no image. Apniko must show its neutral no-photo state until an authorised manufacturer, brand, distributor, supplier or shopkeeper image grant is provided. No Open Food Facts, Open Beauty Facts, Open Pet Food Facts, Open Products Facts, retailer scraping, or guessed/generated product packaging is used.

Photo submission is limited to manufacturer/supplier images with explicit written permission, matching product identity, and administrator review. A candidate remains pending until approved.

## Download, validate and preview

Place an authorised CSV in a local, ignored data folder such as data/catalogue/source/BigBasket.csv. The importer never downloads the file for you.

```bash
python3 -m venv .venv-catalogue
source .venv-catalogue/bin/activate
python -m pip install -r scripts/catalogue/requirements.txt

python scripts/catalogue/import_catalogue.py data/catalogue/source/BigBasket.csv
```

The default is a local dry run. It reports rows, duplicates, skipped products and sample product identities; it makes no API calls and no database changes.

## Apply after permission is verified

1. Start the backend against the intended local database.
2. Obtain an administrator JWT and place it in APNIKO_ADMIN_TOKEN.
3. Confirm the actual written permission covers the dataset and Apniko's intended use.
4. Run the importer with --apply, --permission-reference and --permission-confirmed.

```bash
export APNIKO_ADMIN_TOKEN='YOUR_ADMIN_JWT'
python scripts/catalogue/import_catalogue.py data/catalogue/source/BigBasket.csv \
  --apply \
  --permission-reference 'YOUR-WRITTEN-GRANT-REFERENCE' \
  --permission-confirmed
```

Batches are limited to 500 rows. Each imported record retains source name, source record ID, source URL, permission classification and attribution. Rejected rows are reported; validation is not weakened to increase the import count.

## Resetting the local development catalogue

Run bash scripts/catalogue/reset-local-catalogue.sh from the repository root only after stopping the backend. The helper creates a full MySQL dump on the Desktop before clearing product/catalogue/photo/inventory/cart and development order/payment records. User, role, administrator and shop accounts are retained. The reset is local-only and does not change Git history or any remote database.
