# Product Catalogue Moderation

## Purpose

The catalogue importer retains source records even when product metadata is incomplete or suspicious. Moderation provides an admin review step without deleting products or breaking shop inventory and order references.

## Review states

- `ACTIVE`: included in customer marketplace discovery.
- `NEEDS_REVIEW`: held out of customer marketplace discovery until an administrator checks it.
- `HIDDEN`: deliberately excluded from customer marketplace discovery after an administrator decision.

Legacy products with a null review status remain visible. Shop inventory and historical order records are not deleted or detached when a product is held for review or hidden.

## Automatic checks

New imported products and authoritative refreshes of the same source are assessed unless an administrator has already reviewed the product. Checks flag:
- empty, placeholder, numeric-only or non-descriptive product names;
- product names that appear to contain packaging or OCR text;
- sports nutrition and supplement products that need a catalogue-fit decision;
- products mapped to the unclassified category.

The importer does not delete or permanently reject these records. Recognized retail products remain active when their names and categories pass the checks. Missing pack size or MRP alone does not make a product hidden.

An administrator decision is recorded with the reviewer email and timestamp. Later imports do not overwrite a manual decision.

## Admin API

All endpoints require an authenticated administrator.

### List review queue

`GET /api/admin/catalogue/review-queue?status=NEEDS_REVIEW&page=0&size=50`

Supported statuses: `NEEDS_REVIEW`, `ACTIVE`, `HIDDEN`, and `ALL`. The default status is `NEEDS_REVIEW`. Page size is limited to 100.

### Approve a product

`PATCH /api/admin/catalogue/products/{productId}/review`

```json
{
  "status": "ACTIVE"
}
```

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

Hiding requires a non-empty reason. Returning an item to review clears the previous reviewer audit fields.

## Customer visibility

Customer marketplace shop-product lists, product search, available categories and price comparisons include only products whose status is null (legacy) or `ACTIVE`. Cart additions and quantity updates reject products held for review or hidden; such items already present in a cart are shown as unavailable and excluded from the cart total. Checkout revalidates visibility and rejects stale cart contents. Shop inventory and historical order data are not deleted or detached by moderation.
