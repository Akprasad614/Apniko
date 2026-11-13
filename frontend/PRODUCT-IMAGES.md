# Product image delivery

## Storage design

Apniko stores product metadata and the product image URL in MySQL. The image bytes should live in managed object storage with image CDN delivery (for example, Cloudinary or an S3-compatible service with a CDN in front).

This keeps the relational database small and lets the image service handle resizing, format conversion, caching and delivery.

- `products.image_url`: nullable HTTPS URL for the product's primary image.
- `InventoryResponse.imageUrl`: carries the image URL into marketplace and shop inventory responses.
- Customer product cards prefer the saved product image URL.
- When a product has no saved image yet, the frontend uses a category-level fallback photo.
- If both the product image and category photo fail to load, the card displays a safe local text placeholder.

The image URL must be an HTTPS URL. The customer image component rejects non-HTTPS values before rendering.

## Upload workflow

The product-image upload interface and provider credentials belong in the shopkeeper product catalogue phase. The browser must request a short-lived signed upload or upload token from the backend; provider secrets must never be placed in `VITE_*` variables or shipped to the browser.

A future upload flow should:
1. Shopkeeper selects an image and sees a preview.
2. Backend checks the authenticated shopkeeper and allowed file size/type.
3. Backend issues a short-lived upload signature or accepts and validates the file server-side.
4. Image provider stores the original and creates optimized variants.
5. Backend saves the HTTPS delivery URL against the catalogue product.
6. Marketplace responses expose that URL.

## Catalogue quality

Use one canonical image per catalogue product, rather than separate copies for every shop. Individual shops can later override the catalogue image only if the product packaging or variant genuinely differs.

Use images that Apniko or the participating shop has permission to use. Do not scrape arbitrary Google Images results. Keep a category fallback until a product-specific image is available.

## Current Phase 1 scope

Phase 1 adds the database field, API response field, secure URL selection, category fallback images and customer card rendering. It does not yet add an upload provider, provider credentials or a shopkeeper upload screen; those belong to the shopkeeper product-management phase.
