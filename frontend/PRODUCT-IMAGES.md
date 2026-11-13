# Product image delivery

## Data model and precedence

Apniko supports a shared catalogue image and an optional shop-specific image.

- `products.image_url`: nullable image URL for the canonical product catalogue image.
- `shop_inventory.image_url`: nullable per-shop override for the actual package, brand or variant sold by that shop.
- Customer and shopkeeper inventory responses expose `imageUrl`.
- A shop-specific image takes precedence over the shared product catalogue image.
- If neither image is available, the customer app uses a category-level fallback photo.
- If an image fails to load, the card shows a safe text placeholder.

MySQL stores URL strings only. It does not store image bytes.

## Shopkeeper upload flow

Shopkeepers can add or replace a photo from **Shopkeeper → Inventory → Add product photo**.

- Accepted image formats: JPEG and PNG.
- Maximum file size: 5 MB.
- Maximum dimensions: 6000 × 6000 pixels.
- The backend checks the actual file signature and verifies the file can be read as an image; it does not trust the browser-supplied MIME type or filename.
- The upload endpoint requires an authenticated shopkeeper and verifies that the inventory item belongs to a shop owned by that account.
- Files receive random UUID filenames. A previous per-shop image is removed after its replacement is saved.
- Public GET access is limited to the product-media path. Uploads remain protected by the shopkeeper role.
- The browser sends the file as multipart form data. Provider or storage credentials are never exposed in frontend variables.

## Development storage

By default, the Spring Boot backend stores photos under `uploads/products` relative to the backend working directory. You can configure the path using `APNIKO_IMAGE_STORAGE_DIR`.

The public URLs use the same-origin path `/api/media/products/{generated-file-name}`. This works with the local Vite `/api` proxy and does not require a separate image host during development.

The backend creates the storage directory automatically. Back up this folder together with the database when using the local-file storage adapter.

## Production deployment

The current adapter is a local-filesystem implementation. It is suitable for development and a single backend host with a persistent mounted volume. Before deploying multiple backend instances or using ephemeral containers, move the adapter to managed object storage (for example, S3-compatible storage or Cloudinary) with CDN delivery, or mount shared persistent storage.

The storage interface should continue to return an HTTPS URL. The frontend accepts HTTPS image/CDN URLs and the trusted same-origin Apniko media path. Never store image blobs in MySQL, and never scrape arbitrary Google Images results. Use photos that Apniko or the participating shop has permission to use.

## Operations

- Keep `uploads/products` (or the configured storage directory) outside source control.
- Back up product images and MySQL metadata together.
- Use unique filenames so replacing a photo does not leave browsers serving a stale cached version.
- Configure a persistent volume before production launch.
