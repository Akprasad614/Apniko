# Phase 1 — Customer experience

**Working branch:** `feature/phase-1-customer-experience`  
**Base:** `chore/phase-0-foundation-cleanup`  
**Scope:** Customer-facing discovery, shopping, checkout, order tracking and account routes.

## Implemented customer routes

| Route | Screen |
|---|---|
| `/customer/home` | Local-shop discovery and product storefront |
| `/customer/categories` | Category-first catalogue browsing |
| `/customer/cart` | Basket review, quantity changes and checkout |
| `/customer/orders` | Order history, status timeline and cancellation |
| `/customer/account` | Customer profile and account links |
| `/customer/account/addresses` | Saved delivery addresses and map-assisted address entry |
| `/customer/account/payment-methods` | Available payment methods and configuration state |

The customer shell provides the five primary destinations — Home, Categories, Cart, Orders and Account — with a compact mobile bottom navigation and a wider-screen navigation.

## Discovery and catalogue behaviour

- A unified search accepts shop names/addresses and product queries.
- Shop matches are derived only from public shop name and address fields; owner contact fields are not searched or displayed.
- Product search runs against the existing marketplace endpoint without a shop filter, so it can find currently available items across active shops.
- Selecting a shop opens that shop's own shelf. Selecting a product switches to its shop and filters that shop's shelf using the submitted query.
- Per-shop browsing retains category filtering, name/price sorting, price range filtering and paginated results.
- Product cards use the approved-photo component. Missing or unapproved photos remain an explicit empty photo state; no substitute catalogue photos or generated packshots are seeded.

## Basket and checkout

- Basket data, quantities, availability, stock limits and totals are read from the backend.
- Customers can add items, increase/decrease quantities and remove items.
- Checkout rechecks unavailable/insufficient-stock items and uses the backend cart checkout endpoint.
- Customers can select a saved address or continue without an address for customer pickup; the checkout UI explains that the shopkeeper chooses fulfilment when accepting the order.
- Cash on Delivery is offered as the baseline payment method. Razorpay is shown only when the backend reports it as enabled and uses the provider's hosted checkout and backend signature verification.
- Delivery fees are not invented: the current UI labels them as not configured and does not add an arbitrary fee to the total.

## Orders and account

- Customer order history is fetched from the authenticated `/api/customer/orders` endpoint.
- The order list refreshes every 20 seconds while open and also offers manual refresh.
- Customers can inspect line items and status history. Reopening the detail view fetches the latest status history.
- Cancellation is offered only for `PLACED` orders in the UI; the backend remains authoritative and verifies order ownership and cancellation eligibility.
- Address management supports create, edit, default selection and deletion. Map-based location selection is optional and requires the customer's explicit browser location permission.
- The payment-method screen reports actual backend availability and does not ask Apniko to store card or UPI credentials.

## Empty-catalogue and data integrity

Phase 1 must work while the catalogue is empty. The storefront shows empty states for no shops, categories and products, and the cart and orders screens show their own empty states. It must not add sample products, fake prices, invented stock, generated product images or historical retailer prices.

BigBasket import remains a separately gated data-source activity. No import is performed by this customer-experience phase. Do not enable commercial reuse until written permission covering Apniko's intended use has been verified.

## Test and review checklist

- [x] Customer routes and five-item navigation exist.
- [x] API requests use the shared authenticated `apiRequest` helper.
- [x] Unified shop and cross-shop product discovery added.
- [x] Tests added for privacy-safe shop matching and cross-shop search query construction.
- [x] Backend unit test added for a product search without a shop filter.
- [x] Order history detail now reloads when reopened.
- [ ] GitHub frontend lint, typecheck, Vitest and production build.
- [ ] GitHub backend Maven verification and existing catalogue/supply-chain checks.
- [ ] Static security and dependency scans.
- [ ] Browser verification at narrow mobile and desktop widths against a running local API.
- [ ] End-to-end checkout, payment-provider and order lifecycle verification using a disposable test environment.

CI results and any local-only verification must be recorded against the exact reviewed commit. Creating this branch or pull request does not merge or deploy it.
