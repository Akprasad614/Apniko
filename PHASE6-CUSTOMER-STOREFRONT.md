# Apniko — Customer storefront and shopping journey

## Current implementation

The customer storefront is a mobile-first React/TypeScript experience connected to the existing Spring Boot marketplace, basket, address, payment and order APIs. It preserves the current Phase 1 customer-first design and does not replace it with an older product-card or catalogue layout.

## Discovery and catalogue

- Dedicated customer routes: Home, Categories, Cart, Orders and Account, with nested account routes for addresses and payment availability.
- Loads active shops and category information from the backend.
- The unified discovery search searches public shop names and addresses, and searches product names across active shop inventory.
- Selecting a shop switches the active storefront to that shop. Selecting a product takes the customer to the shop and matching product search.
- The selected shop's catalogue supports text search, category filtering, price range, sorting and pagination.
- Product and shop availability, stock, and selling prices come from backend responses.
- Customer catalogue cards remain text-first and use category marks. No product photographs, generic stock photos or generated product imagery are rendered in this clean-catalogue rollout.
- Customer-facing shop details use public shop name and address only; private owner identity and phone details are not exposed.

## Basket, checkout and payment

- Loads a separate basket for the selected shop.
- Adds, updates and removes basket items through the customer cart API.
- Validates available stock and unavailable items before checkout.
- Uses the backend basket total as the checkout amount.
- Loads the customer's saved addresses and payment methods enabled by the backend.
- Creates orders through the shop-specific checkout endpoint.
- Supports COD. Razorpay initiation is available only when backend configuration enables it.
- Submits the provider response to the backend verification endpoint. An unverified browser callback is never treated as a successful payment.
- Does not store card numbers, UPI PINs or payment-provider secrets.

## Orders and account

- Customer order history uses the authenticated customer's order API.
- Displays backend order status and status history.
- Cancellation is available only while the backend permits it.
- Account routes link to saved-address management, payment-method availability and order history.
- Address selection uses the existing address/map integration when the customer chooses to use it. No unsupported shop-distance or delivery-time claim is displayed.

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

Backend authorization, product availability, stock validation and price calculations remain authoritative. Search results use the current backend data; no sample catalogue is introduced.

## Data and release boundaries

- The Mendeley packaged-food source does not supply retail barcodes, pack sizes, product photos, current shop prices or verified MRPs.
- No sample catalogue, fake inventory, generated product packshots, Open Food Facts data or scraped retailer data is used.
- Product photographs, image imports, image URLs and photo-approval workflows are excluded from Apniko. Customer, shopkeeper and administrator product surfaces remain text-only.
- COD/Razorpay availability depends on backend and merchant configuration in the target environment.

## Verification

Frontend CI runs the repository's Phase 5 and Phase 6 verifiers, dependency installation, ESLint, TypeScript checks, Vitest and the production build. Phase 7 contains the separate full-stack browser, accessibility and mobile acceptance suite.
