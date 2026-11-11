# Apniko — Customer Storefront milestone

## Scope

This change replaces the customer-only placeholder portal with a working customer marketplace screen. It does not change Spring Boot controllers, services, DTOs, database entities, schema, authentication rules, or any non-customer role portal.

## Live backend contracts used

- `GET /api/customer/marketplace/shops` — active Apniko shops.
- `GET /api/customer/marketplace/categories` — available marketplace categories.
- `GET /api/customer/marketplace/products/search` — paged inventory search using the backend's `q`, `category`, `shopId`, `page`, `size`, `sortBy`, and `direction` parameters.
- `GET /api/customer/cart/{shopId}` — basket for the selected shop.
- `POST /api/customer/cart/{shopId}/items` — add one unit of a product.
- `PUT /api/customer/cart/{shopId}/items/{productId}` — update quantity.
- `DELETE /api/customer/cart/{shopId}/items/{productId}` — remove a product.

All requests use the existing typed API helper and the signed-in customer's bearer token. Basket state remains shop-specific, matching the backend's customer-plus-shop cart model. Displayed stock, price, shop names, addresses, and categories come from the backend; this interface does not create sample inventory.

## Experience

- Distinct editorial marketplace layout using the existing Apniko tokens and typography.
- Shop selection, product search, category filtering, and name/price sorting.
- Live stock and rupee price display.
- Per-shop basket with quantity controls, remove action, and backend-calculated totals.
- Loading, empty, unavailable, network, and API error states.
- Keyboard focus, labels, live status announcements, and responsive mobile layout.

## Not included yet

- Delivery address capture and checkout submission.
- Payment gateway flow.
- Product photography, reviews, ratings, maps, or location-based distance claims (the current marketplace DTOs do not provide these).
- Shopkeeper, delivery-partner, or admin dashboards.

Checkout remains intentionally separate so the UI does not invent the `DeliveryMode` or payment journey. It will be added after the delivery and payment contracts are reviewed together.

## Local verification

From `frontend/`:

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

From the repository root:

```bash
bash scripts/verify-phase6.sh
```
