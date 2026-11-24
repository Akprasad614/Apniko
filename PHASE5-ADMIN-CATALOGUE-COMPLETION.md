# Phase 5 — Admin catalogue management and bulk intake

**Implementation status: complete for the audited admin intake and moderation workflow.**
**Catalogue population status: source-constrained; no unverified products are published.**

## What the administrator can do

- Create a master catalogue product and maintain category/subcategory data.
- View the paginated moderation queue for `NEEDS_REVIEW`, `ACTIVE`, and `HIDDEN` records.
- Search review records by product name, brand, barcode, category and subcategory.
- Approve a product, return it to review, or hide it with a recorded reason.
- Reviewers and timestamps are retained by the backend.
- Add a product to a shop only after it is an eligible active master catalogue item; shopkeepers then set their own shop price, stock and availability.

The customer-facing admin workspace uses `AdminDashboard` and `CatalogueReviewQueue`. The backend import endpoint is administrator-only:

- `POST /api/admin/catalogue/import`
- `GET /api/admin/catalogue/review-queue`
- `PATCH /api/admin/catalogue/products/{productId}/review`

## Bulk intake

The supported bulk workflow is the checked-in Python CLI and the admin-only batch API. It is intentionally dry-run-first; it does not scrape retailer websites or download third-party datasets itself.

From the backend repository root, create the importer's isolated Python environment:

```bash
python3 -m venv .venv-catalogue
source .venv-catalogue/bin/activate
python -m pip install -r scripts/catalogue/requirements.txt
```

Inspect a locally obtained copy of the exact, reviewed Mendeley v1 CSV:

```bash
python scripts/catalogue/import_catalogue.py /path/to/indian-packaged-foods-v1.csv
```

The importer checks the 25-column schema, 852-row count, source checksum, row identity, source-quality issues and stable record IDs. It reports the eligibility and rejection counts without making API calls or database changes.

The CLI supports small, bounded batches (maximum 250 rows), explicit `--apply`, and separate `--license-confirmed` / `--commercial-use-confirmed` attestations. Applying also requires the administrator token and a local or HTTPS API target. The backend independently limits batches, validates the exact allowed source and licence, and retains source provenance.

**Important source limitation:** this Mendeley dataset is a packaged-food research/reference source, not a complete retail SKU feed. It lacks verified retail GTIN/barcode and retail pack size. Its rows are held in `NEEDS_REVIEW`; they cannot be made customer-visible until an administrator has independent evidence for those retail identity fields. No current selling price, MRP, pack size, stock, nutrition fact or barcode is inferred from the source.

## Data-source and publication policy

- Keep Open Food Facts and derivatives excluded.
- Do not scrape BigBasket or copy retailer descriptions, pictures, prices or private feeds.
- Do not import synthetic/example product rows.
- Do not treat an arbitrary public dataset or Kaggle mirror as commercially reusable.
- Admit another source only after its owner, reuse/commercial rights, attribution, record identifiers, sellable variant fields and update terms have been reviewed and documented.
- Product photos and image infrastructure remain excluded by Phase 4.

## Acceptance evidence

The Phase 5 admin intake verifier checks the API role gate, bounded batch contract, dry-run-first CLI, pinned source validation, review queue, and moderation tests. Backend CI runs it alongside Phase 3 and Phase 4 policy checks.

**Implementation milestone:** complete. **Real retail catalogue publication:** requires a qualifying source and verified retail-variant facts; this is not fabricated or silently marked as done.
