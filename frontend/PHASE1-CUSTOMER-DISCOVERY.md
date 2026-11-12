# Phase 1 — Customer Product Discovery

## Customer journey

The customer storefront opens in **Products** mode. It searches the existing marketplace catalogue across active shops and shows product, price, stock and shop information together.

Customers can switch to **Shops** mode, search active stores by shop name, owner name or the shop address returned by the backend, and open a shop. Opening a shop switches the product search to that shop's inventory. Customers can return to the all-shops product catalogue at any time.

The existing customer address book, per-shop basket, checkout, order history and cancellation flows remain connected.

## Existing marketplace APIs used

- `GET /api/customer/marketplace/shops` — active shops.
- `GET /api/customer/marketplace/categories` — available categories.
- `GET /api/customer/marketplace/products/search` — product discovery, optional query/category/shop filters, paging and name/price/shop sorting.
- `GET /api/customer/marketplace/products/{productId}/prices` — backend price comparison endpoint remains available for future product detail expansion.
- `/api/customer/cart/{shopId}` — separate cart for each shop.

The frontend does not combine products from different shops into one checkout. Adding a product opens that product's shop-specific cart.

## Product images

The product catalogue has a nullable `image_url` field. Inventory responses include `imageUrl`, so customer product cards can render the image configured for a catalogue product.

The actual image file should be stored on a dedicated image host/object-storage service or CDN; MySQL stores only the HTTPS URL. The backend accepts HTTPS URLs, not uploaded binary files. Product creation can include `imageUrl` in the admin-only product payload; admins can also update or clear it through:

```http
PUT /api/products/{productId}/image
Content-Type: application/json

{ "imageUrl": "https://your-image-host.example/products/item.webp" }
```

The customer UI shows a category fallback image when a catalogue image has not yet been configured or fails to load. Fallback imagery is illustrative category photography, not a claim that the photograph is the exact item sold by a shop. Shopkeepers should use real product photography and a persistent image-host URL when the upload workflow is introduced.

Never put image-provider credentials or storage secrets in Vite environment variables or browser code.

## Nearby-shop boundary

The current shop endpoint returns a shop's name, owner, phone, address and active state. It does not currently return latitude/longitude, a delivery radius or a distance from the customer's saved address.

Accordingly, this phase lets customers discover and choose active Apniko shops, and search shop names/addresses. It does not invent distance labels or claim that the list is nearest-first. To enable true "near me" ranking, a later backend change must add verified shop coordinates and a customer-location/geocoding flow, plus server-side eligibility filtering by delivery radius.

## Validation

Run these frontend checks from `frontend/`:

```bash
npm ci
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

Run backend verification from the repository root:

```bash
./mvnw -B -ntp verify
```

CI remains the authoritative automated validation for the pull-request head. A real customer-session browser check is still required to confirm the display with the user's local data and configured product images.
