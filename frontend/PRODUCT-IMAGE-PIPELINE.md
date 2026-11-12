# Apniko product image pipeline

## Storage decision

Do not store photo binaries in MySQL. Product records store an optional `image_url` string (maximum 1024 characters); the image file itself should live in object storage or a CDN.

This keeps relational records small and lets the application serve resized, cached image variants without loading database BLOBs for every product card.

## Current implementation

- `products.image_url` is an optional JPA field. The existing Hibernate schema update adds the nullable column to a development database.
- The product creation API accepts `imageUrl` and validates that it is an HTTP or HTTPS URL.
- Customer marketplace responses expose `imageUrl` and the shop address.
- `ProductImage` prefers the exact catalogue URL, then uses a curated Unsplash category photo for vegetables, fruit, bread and snacks, then a local category-symbol fallback.
- Images load lazily, use `object-fit: contain`, and fall back automatically when an external URL fails.

Example product creation payload:

```json
{
  "name": "Fresh Apples",
  "category": "Fruits",
  "imageUrl": "https://cdn.example.com/apniko/products/fresh-apples.webp"
}
```

The URL above is illustrative only. Apniko should store the actual URL returned by its configured image provider.

## Production storage provider

Use an object-storage/CDN service such as Supabase Storage, Cloudinary or an S3-compatible bucket. The future upload flow should:

1. Authorize the shopkeeper or administrator on the backend.
2. Validate file type, file size and image dimensions.
3. Upload the original to private or controlled object storage.
4. Produce a public, cached delivery URL or signed read URL.
5. Store only the returned URL and ownership metadata in MySQL.
6. Keep old image URLs or a change log where product history requires it.

Do not put provider secrets in `VITE_*` environment variables. Browser-exposed environment variables are public. Upload credentials or signing keys must stay on the backend.

## Image attribution and quality

The initial category fallbacks use photos marked as free to use under the Unsplash License. The fallback photos are category-level illustrations of a grocery shelf, not a claim that they depict the exact item, brand, pack size or shop inventory. An exact `image_url` from the product catalogue always takes precedence.

Before a commercial launch, replace generic category imagery with shop-approved product photos or catalogue images whose usage rights and provenance are recorded. Avoid scraping image search results or using copyrighted product photos without permission.
