# Phase 3 — authorised product photography

**Working branch:** `feature/phase-3-authorized-product-photography`  
**Base:** `feature/phase-2-authorized-catalogue-intake` (Phase 2 PR #59)  
**Scope:** supplier/manufacturer-authorised photo intake, strict product matching, administrator moderation, audit history and rollout reporting.

## Source decision

Phase 2 uses only the Mendeley Indian Packaged Foods Nutritional Composition Dataset (2026), Version 1, DOI [10.17632/rhswn9z9xr.1](https://doi.org/10.17632/rhswn9z9xr.1), identified as CC BY 4.0. That dataset contains no product photographs, GTINs/barcodes or retail pack sizes. Its licence does not grant rights to images that are not present in the dataset.

Phase 3 is a separate image-rights workflow. It does not add another product-data dataset and does not change Phase 2's Mendeley-only source contract. No retailer catalogue, retailer image, scraped page, Open Food Facts-family source, generated packshot or stock image is an approved source.

## Accepted image-rights routes

1. Manufacturer, brand or authorised distributor image feed with documented rights covering Apniko's intended display and commercial use.
2. A participating supplier/shopkeeper image submission with a documented usage grant covering the same use.
3. An openly licensed image asset may be considered only when its specific licence permits the intended use and all attribution, modification and share-alike conditions can be met. **The current Phase 3 importer/backend contract does not yet accept open-licence candidates**; a separate licence-aware intake path and tests are required before those assets can be submitted.

A public URL, search result, CDN URL or supplier relationship alone is not evidence of image reuse rights. For the current supplier importer, retain a documented permission reference and attribution. Do not require a separate permission letter when a valid open licence already grants the required rights; retain the licence and attribution evidence instead, but do not submit such assets through the current supplier-only importer.

## Import manifest and execution

The dry-run-first importer is `scripts/catalogue/import_authorized_product_photos.py`. Its CSV template is `scripts/catalogue/templates/authorized_product_photos.example.csv`.

Required columns:

- `barcode`
- `source_product_name`
- `source_brand`
- `source_pack_size`
- `image_url`
- `source_url`
- `authorization_reference`
- `attribution`

The importer:

- Requires a valid GTIN-8/12/13/14 check digit and an existing Apniko product awaiting a photo.
- Requires exact normalized brand and pack-size matches; it does not guess variants or pack conversions.
- Requires HTTPS source and image URLs, rejects credentials, query strings and fragments, and permits only the source host or an explicitly configured authorised image CDN.
- Requires an internal permission reference and attribution for the supplier-authorised path.
- Uses an administrator JWT and submits candidates only when `--apply` is explicitly supplied.
- Does not download, copy, transform or rehost image bytes.
- Defaults to dry-run and never approves or publishes a candidate.

Example dry run:

```bash
python scripts/catalogue/import_authorized_product_photos.py \
  --file /path/to/authorised-photo-feed.csv
```

After verifying every row's rights evidence and exact product match, an administrator may explicitly submit candidates for review:

```bash
APNIKO_ADMIN_TOKEN='…' python scripts/catalogue/import_authorized_product_photos.py \
  --file /path/to/authorised-photo-feed.csv --apply
```

Keep supplier agreements outside Git. Store only a controlled internal permission reference in Apniko. Configure any separate image CDN by exact hostname in `APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS`; wildcards are not permitted.

## Important Mendeley matching limitation

The Mendeley records do not include GTINs, retail pack sizes or images. Therefore a Mendeley row alone cannot satisfy Phase 3's exact packaged-variant match. Before a photo candidate can be submitted, the product must have independently verified retail identity data (including GTIN and pack size) and a matching authorised image. Do not infer or manufacture these fields from nutrition serving size or product names.

If a product has no verified GTIN/pack identity, leave it without a photograph until its identity is independently established. The customer experience must continue to use the neutral no-photo state.

## Backend moderation and publication boundary

The admin photo API is protected by the ADMIN role. Candidate submission is checked server-side for GTIN identity, source classification, permission reference, brand, pack size and allowed image host. Candidates enter the PENDING queue.

The administrator review UI displays the candidate image, source record, licence, attribution, permission reference and product identity. Approval requires an explicit reviewer confirmation that the exact product/brand/variant/pack match and image-use rights were checked. Rejection requires a reason. Pending and rejected candidates are not customer-visible; only approved images are exposed.

The retirement workflow records candidate metadata and reviewer rationale in photo review history before clearing an old pending candidate. An already-approved image is preserved when a replacement candidate is retired. Previously rejected/retired image URLs cannot be resubmitted for the same product.

## Verification

Run from the repository root after the Phase 2 Python test environment is prepared:

```bash
scripts/verify-phase2.sh
scripts/verify-phase3.sh
```

Phase 3 verification compiles the authorised supplier importer and runs its unit tests. Backend Maven tests and frontend lint/typecheck/unit tests/build remain part of the repository CI workflows.

## Honest completion and rollout reporting

Phase 3 engineering completion is distinct from real-world photo coverage. Report these separately:

- Importer, API, moderation UI, audit history and automated-test status.
- Number of verified catalogue products eligible for photo matching.
- Number of authorised image candidates submitted, pending, approved, rejected and retired.
- Number of products with an approved photograph.
- Number of supplier feeds and documented rights grants actually received.
- Products intentionally left without images because exact identity or rights evidence is missing.

At the current Phase 2 baseline, the Mendeley dry-run made no API calls or database changes and the development catalogue remains empty. Consequently there are no real product images to import or approve yet. This is not a reason to fabricate sample images or claim content rollout completion.

## Phase 3 exit criteria

- [x] Dry-run-first authorised supplier-photo manifest importer exists.
- [x] Exact GTIN, brand and pack-size matching is enforced before candidate submission.
- [x] HTTPS source/image URL and explicit CDN-host restrictions are enforced.
- [x] Supplier permission reference and attribution are retained with each candidate.
- [x] Backend ADMIN-only candidate submission and review endpoints exist.
- [x] Candidate approval requires explicit identity and rights confirmation.
- [x] Rejection requires a reason; pending/rejected images remain hidden from customers.
- [x] Candidate retirement preserves audit history and previously approved images.
- [x] Rollout reporting distinguishes approved photos from pending/rejected/retired candidates.
- [x] Phase 3 verification script runs the authorised importer test suite.
- [ ] A real supplier/manufacturer feed or openly licensed image manifest is supplied and rights-checked.
- [ ] Product GTIN and retail pack identity are independently verified for each image match.
- [ ] Local/staging dry-run is performed against actual authorised input.
- [ ] Candidate submission and administrator visual review are performed against real products.
- [ ] Approved-image coverage is measured after review; no coverage is assumed from code completion.

**Status:** Phase 3 software workflow is implemented on this branch. Real-world image sourcing, product matching, review and publication remain gated on genuine authorised image inputs and verified product identities. No product image is approved or published by this implementation alone.
