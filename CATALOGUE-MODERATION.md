# Product Catalogue Moderation

## Purpose

The catalogue importer preserves source records even when product metadata is
incomplete or suspicious. Moderation is the publication gate; it must never
turn missing facts into guesses.

## Review states

- `ACTIVE`: included in customer marketplace discovery.
- `NEEDS_REVIEW`: held out of customer discovery and shopkeeper product selection.
- `HIDDEN`: deliberately excluded from customer discovery after an administrator decision.

Legacy products with a null review status remain visible for backwards
compatibility. Shop inventory and historical order records are not deleted or
detached when a product is held for review or hidden.

## Mendeley reference-row restriction

The Mendeley Indian Packaged Foods Dataset (2026) does not contain a verified
GTIN/barcode or retail pack-size column. All rows imported from it are placed
in `NEEDS_REVIEW`, even if the row has no other quality warning. The backend
rejects an attempt to set one of these records to `ACTIVE` until a verified
GTIN and retail pack size are present. The current review queue does not invent
or infer those values.

## Automatic checks

New imports and authoritative refreshes are assessed for:
- Empty, placeholder, numeric-only or non-descriptive product names.
- Product names that appear to contain packaging or OCR text.
- Sports nutrition and supplement products that need a catalogue-fit decision.
- Products mapped to an unclassified category.
- Mendeley reference rows that lack sellable retail identity.

Administrators' decisions are recorded with reviewer email and timestamp.
Subsequent source imports do not override manual decisions, except that the
Mendeley retail-identity safety rule always re-queues an incomplete reference
row.

## Admin API

All endpoints require an authenticated administrator.

### List review queue

`GET /api/admin/catalogue/review-queue?status=NEEDS_REVIEW&page=0&size=50`

Supported statuses: `NEEDS_REVIEW`, `ACTIVE`, `HIDDEN`, and `ALL`.
Page size is limited to 100. The optional `q` term matches product name,
brand, barcode, category and subcategory.

### Approve a product

`PATCH /api/admin/catalogue/products/{productId}/review`

```json
{
  "status": "ACTIVE"
}
```

Mendeley reference rows without both verified barcode and retail pack size
are rejected by the backend with a clear validation error.

### Hide a product

```json
{
  "status": "HIDDEN",
  "reason": "Not suitable for the Apniko customer catalogue"
}
```

### Return a product to the review queue

```json
{
  "status": "NEEDS_REVIEW",
  "reason": "Please verify the product label"
}
```

Hiding requires a non-empty reason. Returning an item to review records the
administrator and timestamp while preserving the review reason.

## Customer visibility

Customer marketplace shop-product lists, search, available categories and
price comparisons include only products whose status is null (legacy) or
`ACTIVE`. Cart additions and quantity updates reject products held for review
or hidden; checkout revalidates visibility and rejects stale cart contents.
