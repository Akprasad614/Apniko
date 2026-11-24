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
- Customer shop responses expose only the shop ID, public name, address, active state and delivery mode. Owner names and phone numbers are not included in the customer marketplace DTO.
- Product search runs against the existing marketplace endpoint without a shop filter, so it can find currently available items across active shops.
- The backend search filters to active shops, enabled inventory, positive stock and customer-visible catalogue products.
- Selecting a shop opens that shop's own shelf. Selecting a product switches to its shop and filters that shop's shelf using the submitted query.
- Per-shop browsing retains category filtering, name/price sorting, price range filtering and paginated results.
- Customer product surfaces are text-first. They do not render product photos; abstract category marks provide visual grouping without pretending to be product packaging or photographs.
- Product name, brand, verified variant/pack details, current shop price and stock continue to come from backend records. No catalogue examples, prices or stock values are fabricated.
- Category shortcuts and empty-catalogue category tiles are navigation affordances only. They do not create database categories or products.

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

Phase 1 did not import catalogue data. The later Phase 2 decision selected only the inspected Mendeley Indian Packaged Foods Nutritional Composition Dataset (2026), Version 1, DOI `10.17632/rhswn9z9xr.1`, under its stated CC BY 4.0 terms. Its importer and dry-run are documented in `PHASE-2-AUTHORIZED-CATALOGUE-INTAKE.md`. Open Food Facts and related catalogue feeds remain excluded. Phase 3 photographs are governed separately by `PHASE-3-PRODUCT-PHOTOGRAPHY.md`; Mendeley's dataset licence does not cover photographs absent from the dataset.

## Test and review checklist

- [x] Customer routes and five-item navigation exist.
- [x] API requests use the shared authenticated `apiRequest` helper.
- [x] Unified shop and cross-shop product discovery added.
- [x] Shop owner names and phone numbers are excluded from customer marketplace response DTOs.
- [x] Tests cover privacy-safe shop matching and cross-shop search query construction.
- [x] Backend unit test covers cross-shop product search and the public customer shop response shape.
- [x] Order history detail reloads when reopened.
- [x] Owner completed local visual review at mobile and desktop widths; customer storefront visuals were confirmed.
- [x] Frontend, backend, catalogue-tooling, CodeQL, static-security, secret, dependency and SBOM checks passed on the reviewed Phase 1 head.
- [ ] End-to-end checkout, payment-provider and order lifecycle verification using a disposable test environment.

The end-to-end item remains intentionally pending: the production-like catalogue is empty, so real add-to-cart/checkout cannot be exercised without a disposable test fixture and a configured payment-provider test account. CI and local visual review do not substitute for that test. Creating this branch or pull request does not merge to `main` or deploy it.
