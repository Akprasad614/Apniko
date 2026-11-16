# Phase 3 — authentic product photographs

## What customers see

A product photograph appears in the customer marketplace only after it has been approved by an administrator. Missing or broken images use a neutral photo-unavailable state. Apniko does not invent packaging, fill gaps with generic stock photography or use generated product imagery.

## Matching and source

The first importer uses the Open Facts API v3 product lookup with the catalogue's existing GTIN. The source barcode must match exactly. A product-name-only match is never enough. The importer reads the selected front image from the API's selected image variants and requires the corresponding Open Facts image host and product barcode in the image path.

Open Facts records are community contributed. A barcode match does not prove that the pack name, variant, size or photograph is correct, so an administrator must compare those details before approval.

## Copyright and attribution

The source image licence, attribution and source product page are stored with the approved photograph and shown in the customer UI. Open Food Facts describes product images as CC BY-SA and notes that the images may contain additional copyrighted elements. The stored licence is provenance, not a guarantee that all visible package artwork is cleared for every commercial use. Verify the actual image and current source terms before approval and production use.

Retailer product images from BigBasket, Instamart, Flipkart Minutes, Amazon and JioMart are not copied or scraped. Their interfaces may be used as layout inspiration only.

## Dry-run importer

Install the catalogue script dependencies in the same environment used for Phase 2:

    python3 -m venv .venv-catalogue
    source .venv-catalogue/bin/activate
    python -m pip install -r scripts/catalogue/requirements.txt

Set the Apniko URL and an administrator JWT:

    export APNIKO_API_URL="http://localhost:8080"
    export APNIKO_ADMIN_TOKEN="YOUR_ADMIN_JWT"

Preview candidates without changing Apniko:

    python scripts/catalogue/import_product_photos.py --limit 10

Only after reviewing the proposed matches, submit them to the admin queue:

    python scripts/catalogue/import_product_photos.py --limit 10 --apply

The importer waits at least four seconds between source lookups, stays within the published 15 product-read requests/minute limit, uses a descriptive User-Agent, skips source HTTP 404 responses as products not found, stops on 429/503, and caps each run at 100 products. Other request failures remain visible so they are not mistaken for missing products. Do not run multiple importers concurrently.

## Admin review workflow

1. Run a dry-run for barcode-backed products.
2. Submit candidates with --apply.
3. Open Admin → Product catalogue → Product photo review.
4. Compare the Apniko name, brand, GTIN and pack size against the source record and actual photo.
5. Approve only an exact pack match with source attribution and licence. Reject uncertain matches with a reason.

A pending image is never exposed to customers. Rejecting a replacement retains the previous approved photo. Products without a GTIN are skipped rather than guessed.

## Scope boundaries

This phase adds an authentic photo lookup, attribution, moderation and marketplace display. It does not change prices, stock, catalogue identity, fulfilment or orders. No image files are committed to the repository.
