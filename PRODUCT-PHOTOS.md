# Product photography — clean restart policy

## Current state

Apniko starts with no products and no product photographs. No product image URL is imported from the BigBasket CSV, and the user interface must show its neutral "Photo not yet available" state until a properly authorised photograph exists.

## Permitted image source

Only manufacturer, brand, authorised distributor, participating supplier or shopkeeper-provided images with explicit written permission for Apniko's intended display and commercial use may be submitted. Public reachability is not permission.

Do not use Open Food Facts, Open Beauty Facts, Open Pet Food Facts, Open Products Facts, scraped retailer pages, or guessed/generated product package images. Do not use BigBasket product images unless BigBasket has provided express written permission.

## Matching and review

For a packaged product, confirm the actual product name, brand, variant/flavour/formulation, printed pack size and the image of the exact front package. Where the supplier provides a GTIN, it must match the Apniko product exactly. A missing GTIN must not be invented.

Photo candidates must include an HTTPS source page, HTTPS image URL, source product name/brand/pack size, an internal permission reference and attribution. If the image uses a separate CDN, configure its exact hostname in APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS; wildcard hosts are not allowed.

The server accepts only 'Brand / supplier authorised catalogue' candidates with the MANUFACTURER_AUTHORIZED permission classification. All candidates enter the pending admin queue. Only an administrator can approve after verifying image identity and permission evidence. Pending/rejected candidates are never customer-visible.

## Supplier CSV intake

Use scripts/catalogue/templates/authorized_product_photos.example.csv as the manifest format:

```bash
python scripts/catalogue/import_authorized_product_photos.py \
  --file /path/to/authorised-photo-feed.csv
```

This is a dry run. To submit valid records for administrator review, add --apply only after verifying the written grant and the actual product/image match.

The importer does not download or rehost image bytes. Keep confidential supplier agreements outside the repository and store only an internal permission reference in Apniko.

## No image is better than a wrong image

If an exact product photograph and reuse permission cannot be verified, leave the product without an image. Do not fill empty image states with a different product, store photograph, generic pack shot, scraped thumbnail or generated packaging.

## Rollout reporting

Report separate counts for products imported, products with approved photos, pending photo candidates, authorised supplier feeds/permission grants received, and products intentionally left without photos. A working importer or review UI is not evidence that image sourcing is complete.
