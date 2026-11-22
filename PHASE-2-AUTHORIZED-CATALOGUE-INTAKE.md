# Phase 2 — Authorised catalogue intake

**Working branch:** `feature/phase-2-authorized-catalogue-intake`  
**Base:** `chore/phase-0-foundation-cleanup`  
**Starting point:** Phase 1 customer experience merged at `6202c0fdc498135789ac1ecabd27e3cc4ee1a146`.

## Objective

Activate Apniko's empty master catalogue using only a dataset whose reuse rights cover Apniko's intended use. BigBasket is the planned source. Open Food Facts and its related databases are excluded.

This phase does not seed demo products, invent product identities, add substitute imagery or use historical retailer prices as current shop prices.

## Existing implementation carried into this branch

The Phase 0/Phase 1 baseline already includes:

- A file-based BigBasket CSV importer with dry-run as the default.
- Field mapping for product name, brand, category, subcategory, type/variant and description.
- Stable source-record identity and duplicate-row reporting.
- Explicit import batch limits and administrator-authenticated API submission.
- Permission-reference and confirmation requirements in the importer and the backend catalogue import contract.
- Catalogue provenance fields for source name, source URL, permission classification, attribution and permission reference.
- Product-quality assessment and an admin moderation queue with ACTIVE, NEEDS_REVIEW and HIDDEN states.
- Tests for BigBasket field mapping, duplicate detection, dry-run behaviour, the permission gate, moderation, quality rules and catalogue reset safety.

The importer intentionally does not import retailer sale prices, market prices, ratings, images, GTINs/barcodes or guessed pack sizes from the commonly circulated CSV schema.

## Hard external gate — do not bypass

A public Kaggle copy does not prove that the underlying BigBasket catalogue may be reproduced or used commercially. A licence shown for a Kaggle notebook does not establish the dataset licence.

**Do not run an applying import** until both are available and reviewed:

1. Written permission or a dataset licence that explicitly covers reproduction and commercial use of the relevant catalogue fields in Apniko.
2. The exact CSV snapshot supplied from that authorised source, with its provenance and permission reference recorded.

Do not scrape BigBasket pages, call undocumented retailer APIs, copy retailer images or treat publicly reachable content as licensed. If the permission cannot be verified, keep the importer in dry-run mode and leave the production-like catalogue empty.

## Phase 2 execution sequence

1. **Verify the grant:** record the permission issuer, scope, covered data fields, permitted uses, attribution obligations, duration/territory if applicable, and a retrievable permission reference. Escalate ambiguous terms for legal review; do not infer rights.
2. **Inspect the exact snapshot:** confirm the CSV schema, origin, date/version and file hash. Keep the source file local and ignored; do not commit the dataset or credentials.
3. **Run a dry-run:** record rows read, eligible identities, missing names, duplicates and rejected records. Inspect samples for India-market relevance, correct brand/category/variant mapping and non-descriptive or OCR-noise names.
4. **Review the mapping:** do not infer GTINs, pack sizes or MRP. Do not use historical BigBasket prices or ratings as current selling prices. Do not import image URLs.
5. **Apply only to an isolated local/staging database:** use an administrator token, the verified permission reference and the explicit permission confirmation. Never target production as part of this phase.
6. **Moderate results:** keep uncertain or unclassified records in NEEDS_REVIEW. Approve only products with credible identity and category; do not optimise for row count.
7. **Keep inventory separate:** imported master products do not create shop inventory. Shopkeepers must set their own current selling price, stock, availability and low-stock threshold.
8. **Verify the empty/default experience:** no authorised dataset means zero catalogue rows and clear empty states. No fallback sample records are permitted.

## Customer presentation rules

The customer experience remains text-first as confirmed in Phase 1. Product cards must use actual backend product name, brand, verified variant/pack details when present, actual shop price and stock. Abstract category marks are visual grouping only, not product photographs or representations of specific packages.

No product photo pipeline or photo-based customer presentation is part of this phase. Open Food Facts remains prohibited.

## Exit criteria

- [ ] Written data-reuse permission verified and archived with a usable permission reference.
- [ ] Exact authorised BigBasket CSV snapshot and provenance verified.
- [ ] Dry-run report reviewed and accepted; duplicate and rejection counts understood.
- [ ] Product identity mapping reviewed without fabricating missing GTIN, pack size, MRP, prices, ratings or images.
- [ ] Isolated staging import completed only after the permission gate is met.
- [ ] Imported products pass quality checks and admin moderation.
- [ ] No shop inventory, current shop prices or stock is created by master-catalogue import.
- [ ] Customer empty-catalogue and text-first UI tests continue to pass.
- [ ] Backend, frontend, import safety and security CI pass on the final Phase 2 commit.

**Current status:** implementation scaffolding and automated checks are present in the base branch. Real data activation is blocked pending written reuse rights and an authorised CSV snapshot. No data has been imported by this Phase 2 branch.
