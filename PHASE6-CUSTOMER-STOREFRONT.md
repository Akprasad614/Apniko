# Apniko — Customer storefront and shopping journey

## Scope

The customer storefront is the primary mobile-first shopping experience. It is a live React/TypeScript interface backed by the existing Spring Boot marketplace, basket, address, payment and order contracts. This document describes the current working scope; checkout and payment are included in this implementation.

## Discovery and catalogue

- Dedicated customer routes: Home, Categories, Cart, Orders and Account, with nested account routes for addresses and payment availability.
- Loads active Apniko shops and available categories from the backend.
- Offers quick shop search by shop name, shopkeeper name or neighbourhood address, alongside the selected-shop picker.
- Searches live shop inventory using text, category, price range, sort direction and pagination.
- Displays the selected shop's actual product availability, price, stock quantity and catalogue facts returned by the backend.
- Shows a product photograph only when the backend supplies an approved photo with its source/attribution data. Missing or broken images use the neutral no-photo state.
- No ratings, delivery distances, sale prices or stock figures are fabricated.

## Basket, checkout and payment

- Loads a separate basket for the selected shop.
- Add, change quantity and remove basket items through the customer cart API.
- Validates available stock and unavailable items before checkout.
- Uses the backend basket total, not a frontend estimate, as the checkout amount.
- Loads the customer's saved addresses and the payment methods currently enabled by the server.
- Creates the order through the shop-specific checkout endpoint.
- Supports Cash on Delivery. Razorpay checkout is initiated only when the backend reports that method as enabled and supplies the provider configuration.
- Sends the provider response to the backend verification endpoint. The frontend does not treat an unverified browser callback as a successful payment.
- Does not store card numbers, UPI PINs or payment-provider secrets.

## Orders and account

- Customer order history uses the authenticated customer's order API.
- Displays backend order status and status history.
- Cancellation is shown only while the backend permits cancellation.
- Account screens link to saved address management, payment availability and order history.
- The address picker uses the existing map/geocoding integration when the customer chooses to use it; it does not invent a distance to a shop.

## Main backend contracts

- `GET /api/customer/marketplace/shops`
- `GET /api/customer/marketplace/categories`
- `GET /api/customer/marketplace/products/search`
- `GET /api/customer/cart/{shopId}`
- `POST /api/customer/cart/{shopId}/items`
- `PUT /api/customer/cart/{shopId}/items/{productId}`
- `DELETE /api/customer/cart/{shopId}/items/{productId}`
- `POST /api/customer/cart/{shopId}/checkout`
- `GET /api/customer/addresses` and address create/update/delete endpoints
- `GET /api/payments/methods`
- `POST /api/payments/orders/{orderId}/initiate`
- `POST /api/payments/verify`
- `GET /api/customer/orders` and customer-owned order status/cancellation endpoints

All requests use the shared typed API helper and the signed-in customer's bearer token. Backend authorization and stock/price calculations remain authoritative.

## Data and release boundaries

- Shop inventory and selling prices are controlled by each shopkeeper.
- The selected Mendeley packaged-food seed does not supply product images, retail barcodes or retail pack size; no gaps are filled by guessing.
- Product photos are only displayed when the photo workflow reports an approved matching asset.
- No sample catalogue, fake inventory, generated product packshots, Open Food Facts content or scraped retailer data is used.
- Checkout/payment behaviour depends on the backend and payment-provider configuration in the target environment. COD remains available when returned by the backend.

## Verification

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

Frontend CI performs the checks against the committed lockfile. Dedicated browser end-to-end and staging verification remain part of Phase 7.
