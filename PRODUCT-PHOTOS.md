# Product photography — clean restart policy

## Current state

Apniko starts with no products and no product photographs. The selected
Mendeley packaged-food dataset is not an image feed, and its records must not
be paired with unrelated thumbnails or generated product packaging. The
customer storefront remains text-first with a neutral no-photo state.

## Excluded image sources

Do not use Open Food Facts, Open Beauty Facts, Open Pet Food Facts,
Open Products Facts, scraped retailer pages, stock pack shots, guessed
images or generated packaging. Public reachability is not evidence of reuse
rights.

## Future permitted image source

Phase 3 uses manufacturer, brand, authorised distributor, participating
supplier or shopkeeper image submissions, or an image dataset whose licence
explicitly permits Apniko's intended use.

For supplier-provided images, keep a written usage grant covering Apniko's
intended display and commercial use. For an openly licensed image dataset,
record and follow that dataset's specific licence and attribution conditions;
do not request a separate permission letter where the licence already grants
the required rights. A missing documented rights basis means no image is
used.

For a packaged product, confirm the actual product name, brand,
variant/flavour/formulation, printed pack size and the exact front-package
image. Where the supplier provides a GTIN, it must match the Apniko product
exactly. A missing GTIN must not be invented.

Photo candidates must include an HTTPS source page, HTTPS image URL, source
product name/brand/pack size, a licence or internal permission reference and
attribution. If the image uses a separate CDN, configure its exact hostname in
APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS; wildcard hosts are not allowed.

The server accepts only 'Brand / supplier authorised catalogue' candidates
with the MANUFACTURER_AUTHORIZED permission classification. All candidates
enter the pending admin queue. Only an administrator can approve after
verifying image identity and the documented reuse rights. Pending/rejected
candidates are never customer-visible.

## Supplier CSV intake

Use `scripts/catalogue/templates/authorized_product_photos.example.csv` as
the manifest format:

```bash
python scripts/catalogue/import_authorized_product_photos.py \
  --file /path/to/authorised-photo-feed.csv
```

This is a dry run. To submit valid records for administrator review, add
`--apply` only after verifying the usage grant or applicable image licence
and the actual product/image match.

The importer does not download or rehost image bytes. Keep confidential
supplier agreements outside the repository and store only an internal
permission reference in Apniko.

## No image is better than a wrong image

If an exact product photograph and reuse right cannot be verified, leave the
product without an image. Do not fill empty image states with a different
product, store photograph, generic pack shot, scraped thumbnail or generated
packaging.

## Rollout reporting

Report separate counts for products imported, products with approved photos,
pending photo candidates, authorised supplier feeds/rights grants received,
and products intentionally left without photos. A working importer or review
UI is not evidence that image sourcing is complete.
