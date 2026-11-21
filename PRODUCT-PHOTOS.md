# Phase 3 — authentic product photography

## Product presentation standard

Apniko uses product-specific, square, front-facing images with a clean neutral background. Packaged products must show the actual brand, variant and pack size. Loose fruits and vegetables use accurate produce imagery, not invented packaging. Customer product cards are the primary presentation; the same approved image is reused in shopkeeper catalogue selection and inventory.

This is an original Apniko design standard informed by familiar grocery-shopping patterns. Do not reproduce retailer-owned product images, catalogues, descriptions, logos or proprietary interface assets without a written licence.

## Approved source order

1. Manufacturer, brand or authorised distributor feeds with written permission covering Apniko's intended display and commercial use.
2. Images supplied by a participating supplier or shopkeeper with a documented usage grant.
3. Open datasets whose specific image licence permits the intended use, with attribution and share-alike conditions observed.
4. No image, rather than an unrelated, unlicensed, guessed or low-quality image.

Public availability of an image URL is not proof of permission. Product database rights and image copyrights/licences are separate.

## Matching and review

For an Open Facts candidate, the source GTIN must match the Apniko GTIN exactly, the image must come from the matching source image host, and the image path must identify the same barcode.

For any source, the reviewer compares the Apniko product with the source image and record:

- Product name
- Brand
- Variant / flavour / formulation
- Pack size / net quantity
- Exact front photograph
- Source, licence or permission type, attribution and source page

A barcode match alone is not enough to approve a photograph. Loose produce should not be treated as a branded packaged product.

## Photo credits and licence display

For Open Facts photos, use the source's CC BY-SA 3.0 licence deed and provide the source record, available contributor/photographer attribution and a link to the licence alongside the displayed image. Do not replace missing source attribution with an invented creator name. The customer catalogue, shopkeeper product picker and shop inventory use the same approved-photo credit component.

The Open Facts image licence does not automatically clear every right visible in a package photograph. Packaging artwork, trademarks, logos and other graphic elements may carry separate rights. An exact barcode/brand/pack match is necessary for product identity, but it is not proof that every visible element is cleared for commercial reuse. Keep an image out of the published catalogue when its source or rights are uncertain.

Apniko does not crop, retouch, remove branding from or otherwise alter the source image during import. Should a future workflow modify a licensed image, it must record that modification and preserve the licence attribution obligations.

## Manufacturer / supplier authorised images

The admin-only photo candidate API supports a source named `Brand / supplier authorised catalogue`. Such a candidate must:

- Provide the exact Apniko product GTIN
- Provide a source product page on the supplier/brand domain
- Provide source product name, brand and pack size
- Use the permission classification `MANUFACTURER_AUTHORIZED`
- Include a permission reference (for example, a supplier agreement or brand image-use grant reference)
- Use HTTPS and an image host matching the source domain or an explicitly configured image host
- Pass administrator review before appearing in either customer or shopkeeper catalogues

For a separate authorised image CDN, configure exact hostnames (comma-separated, no wildcard) through the environment variable `APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS` or Spring property `apniko.catalogue.photo-authorized-image-hosts`. Keep the written permission itself in your controlled supplier records; do not store confidential contracts in a public repository.

## Open Facts dry-run importer

The first automated lookup uses Open Facts API v3. It is a secondary source, not the primary target for a BigBasket-quality catalogue. Open Facts is community contributed, and records can be incomplete or incorrect. Open Food Facts describes product images as CC BY-SA and notes that images may contain additional copyrighted elements. Verify the current terms and actual image before production use.

Install the catalogue script dependencies:

```bash
python3 -m venv .venv-catalogue
source .venv-catalogue/bin/activate
python -m pip install -r scripts/catalogue/requirements.txt
```

Set the Apniko API URL and an administrator JWT:

```bash
export APNIKO_API_URL="http://localhost:8080"
export APNIKO_ADMIN_TOKEN="YOUR_ADMIN_JWT"
```

Preview candidates without changing Apniko:

```bash
python scripts/catalogue/import_product_photos.py --limit 10
```

An Open Facts 404 is treated as an unavailable source record and skipped. A 429/503 stops the run. Other errors remain visible. The script waits at least four seconds between lookups, uses a descriptive User-Agent and caps each run at 100 products.

The importer only presents a candidate when the exact GTIN, brand and pack-size value all match the Apniko product. Missing or mismatched brand/pack data is skipped. The backend independently enforces the same brand and pack-size match before a candidate can enter the review queue. This is intentionally strict: a barcode match alone must not attach a different variant's photograph.

Only after checking each candidate's product name, brand, pack size and visible packaging may it be submitted:

```bash
python scripts/catalogue/import_product_photos.py --limit 10 --apply
```

Submission only creates a pending review item. It does not approve or publish the image.

## Manufacturer / supplier CSV intake

A supplier may provide an authorised image manifest using the template:

`scripts/catalogue/templates/authorized_product_photos.example.csv`

The CSV must include barcode, source product name, source brand, source pack size, HTTPS image URL, HTTPS source product URL, permission reference and attribution. Brand and pack size must match the corresponding Apniko product exactly after case/whitespace normalization. The script skips unmatched barcodes and mismatched variants rather than guessing.

For images hosted on an authorised CDN separate from the brand site, set a comma-separated exact-host allowlist:

```bash
export APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS="images.brand.example,cdn.supplier.example"
```

Preview the CSV without submitting anything:

```bash
python scripts/catalogue/import_authorized_product_photos.py --file /path/to/authorised-photo-feed.csv
```

Only after verifying the written permission grant and dry-run rows, submit candidates for admin review:

```bash
python scripts/catalogue/import_authorized_product_photos.py --file /path/to/authorised-photo-feed.csv --apply
```

The importer does not download or rehost the image bytes. The server validates the HTTPS source/image hosts, exact GTIN and permission reference. Submission creates pending review records; it does not approve or publish images. Keep confidential supplier contracts outside the repository and record only an internal permission reference.

## Admin review workflow

1. Run a dry-run for products with a verified GTIN.
2. Submit only candidates whose source image is worth a human review.
3. Open Admin → Product catalogue → Product photo review.
4. Compare the Apniko name, brand, GTIN and pack size with the source name, brand, pack size and actual photograph.
5. Approve only an exact product/variant/pack match with valid licence or permission evidence. Reject uncertain matches with a reason.

Pending and rejected candidates never appear in customer or shopkeeper catalogue projections. Rejecting a replacement preserves the previous approved photograph. The approved image is reused by customer cards, shopkeeper product selection and shop inventory.

## Release reporting

Report the code/test state separately from content rollout. Track:

- Catalogue products imported and quality-approved
- Products with approved photographs
- Manufacturer / supplier feeds and permission grants actually received
- Candidates awaiting manual review
- Products with no acceptable photograph yet

Do not mark catalogue photography complete merely because an importer, endpoint or UI has been implemented. Actual supplier-authorised coverage requires real supplier data and permission.


## Phase 3B — explicit approval verification

The administrator review interface opens the candidate image at full size and displays its submission time, source record, source product name, brand, pack size, licence, attribution and permission reference.

Before an administrator can approve a candidate, the administrator must explicitly confirm in the review UI that:

- the visible photograph matches the exact product, brand, variant and pack size; and
- the recorded image licence or permission is suitable for Apniko's intended use.

The backend independently requires the confirmation field on an APPROVED review request. Rejections continue to require a reason and do not require approval attestation. This is an explicit human-review gate, not an automated legal or visual-verification system.

A community-contributed Open Facts image must still be checked for product identity, visible packaging artwork and attribution. A manufacturer/supplier candidate must still have a real, traceable permission reference in controlled supplier records. An empty, expired, unrelated or unverifiable permission reference is not approval evidence.

### Phase 3B rollout completion evidence

Keep implementation checks separate from content completion. The Phase 3B rollout report must record, at minimum:

- total products in the master catalogue;
- products with a verified GTIN and eligible for photo lookup;
- dry-run candidates found and skipped, with reasons;
- pending, approved and rejected photo candidates;
- approved-photo coverage by catalogue category;
- authorised supplier feeds actually received and their permission references;
- products intentionally left without a photograph because no acceptable source exists.

Do not mark Phase 3B content rollout complete until the photographs have been inspected and the source/licence or written permission evidence has been verified. The repository cannot manufacture supplier permissions or substitute illustrative images for genuine product photography.

### Phase 3B refresh sequence (local)

1. Pull the merged code and restart the backend so Hibernate creates the
   `product_photo_review_history` table.
2. In Admin → Product catalogue → Product photo review, inspect the rollout
   report. Retire legacy pending candidates only when they need to be re-reviewed;
   the old image URL and source evidence are archived before the active queue is cleared.
3. From the repository root, run the secure helper:

   ```bash
   python3 -m venv .venv-catalogue
   source .venv-catalogue/bin/activate
   python -m pip install -r scripts/catalogue/requirements.txt
   python scripts/catalogue/refresh_product_photos.py --limit 100
   ```

4. Inspect the dry-run candidate list. To submit exact matches to the pending
   admin queue, repeat with `--apply`. The helper logs in securely, does not print
   the JWT, and does not approve photos.
5. Inspect each submitted image in the Admin queue. Approve only after the visible
   package, variant, pack and commercial rights evidence have been checked.
