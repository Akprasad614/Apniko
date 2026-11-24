# Phase 6 — role-based application delivery

**Status: implementation complete; all Phase 6 PR checks passed.**  
**Working branch:** `feature/phase-6-role-based-app-delivery`  
**Integration target:** `chore/phase-0-foundation-cleanup`. Phase 6 is being integrated into the verified development line without modifying `main`.

Phase 6 completes the customer, shopkeeper, delivery-partner and administrator application workspaces using the existing Spring Boot contracts. It does not seed sample catalogue data, invent stock or sales, create artificial delivery estimates, or add generated product photographs.

## 1. Customer storefront and order journey

The customer workspace keeps its five mobile destinations — Home, Categories, Cart, Orders and Account — and provides:

- Live active-shop selection, with quick search by public shop name or neighbourhood address. Customer discovery does not expose private owner or phone details.
- Search, category and price filtering, sorting and paging against current shop inventory.
- All product catalogue interfaces are text-only across customer, shopkeeper, delivery and administrator roles. Product photographs, image URLs, uploads, product-image moderation and image rollout are excluded and must not be reintroduced.
- Per-shop basket retrieval and add, update and remove actions with backend stock checks and totals.
- Address selection and management through the customer's saved-address endpoints and map picker.
- Checkout against the selected shop, with Cash on Delivery and Razorpay initiation when the backend reports online payments as configured.
- Payment verification through the backend; card and UPI credentials are not stored by the Apniko frontend.
- Order history, status history, progress display and cancellation only while the backend permits it.
- Account links for saved addresses, payment availability and order history.

Relevant APIs include:

- `GET /api/customer/marketplace/shops`, `/categories`, `/products/search`
- `GET /api/customer/cart/{shopId}`, and the cart item POST/PUT/DELETE endpoints
- `POST /api/customer/cart/{shopId}/checkout`
- `GET /api/customer/addresses` and the address-management endpoints
- `GET /api/payments/methods`, `POST /api/payments/orders/{orderId}/initiate`, `POST /api/payments/verify`
- `GET /api/customer/orders` and customer-owned cancellation/status-history routes

## 2. Shopkeeper workspace

The shopkeeper interface now follows the same Apniko visual language as the customer app: shared paper/forest/citrus design tokens, restrained typography, a compact sticky desktop section bar, and a safe-area-aware mobile bottom navigation for Overview, Orders, Inventory and Performance.

Existing operational features remain connected to live shopkeeper endpoints:

- Select among shops assigned to the authenticated shopkeeper.
- Review and filter orders for the selected shop. The dashboard refreshes order state periodically.
- Choose and save per-order fulfilment before accepting an order; view delivery address and order-status history.
- Move an order through the supported shopkeeper workflow, or reject/cancel only where allowed by the backend and with the required reason.
- Browse the shared catalogue, add a verified catalogue item to shop inventory, set the shop-specific price and opening quantity, edit availability/stock, restock with a note, manage low-stock threshold, and inspect the stock movement audit trail.
- View operational overview counts based on the real orders and inventory returned by the backend.
- View performance for 7, 30 or 90 days using the existing analytics API. Summary totals cover all shops linked to the shopkeeper account; the top-product list is scoped to the currently selected shop. Empty periods are shown as empty/zero states, never as sample sales.
- Review stock guidance from the existing weighted moving-average recommendation service, based on recorded order history and current inventory. The UI shows the 28-day history window and three-day lead-time assumption; this is an explainable baseline, not a trained model or guaranteed purchase quantity.

Performance endpoints:

- `GET /api/shopkeeper/analytics/overview?from=...&to=...`
- `GET /api/shopkeeper/analytics/daily?from=...&to=...`
- `GET /api/shopkeeper/analytics/top-products?shopId=...&from=...&to=...&limit=...`
- `GET /api/shopkeeper/ai-insights/replenishment?shopId=...&historyDays=28&leadTimeDays=3`

All metrics and stock recommendation inputs are backend response values. Daily charts are rendered from the response dates and values. The frontend does not estimate revenue, margins or stock turnover.

## 3. Delivery-partner workspace

The delivery workspace includes mobile navigation for Available, My deliveries and Completed, plus:

- A live list of orders that are ready for platform delivery.
- The authenticated partner's assigned active deliveries and completed delivery history.
- Accept, confirm pickup and mark delivered actions.
- Customer delivery destination details where the API returns them.
- Loading, empty, error and action-feedback states.

Relevant APIs:

- `GET /api/delivery/orders/available`
- `GET /api/delivery/orders`
- `PUT /api/delivery/orders/{id}/accept`
- `PUT /api/delivery/orders/{id}/pickup`
- `PUT /api/delivery/orders/{id}/deliver`

The interface does not invent a route, live GPS position or delivery ETA.

## 4. Administrator workspace

The administrator workspace keeps the existing role-protected operational tools and adds consistent mobile navigation for Overview, People, Shops and Catalogue:

- Platform analytics overview from the backend.
- User provisioning for shopkeeper and delivery-partner accounts, user-role filtering, and account activation controls.
- Shop creation/management, ownership assignment and active-state controls.
- Catalogue product administration and category selection.
- Text-only catalogue review/moderation, including verification of retail identity and source provenance.
- MRP source options limited to the supported physical-pack, brand-supplied and government-source references. Excluded catalogue feeds are not offered as a source.

The existing admin API and text-only catalogue review queue remain the source of truth.

## 5. Shared notifications

A shared notification centre is now available in all four authenticated role headers. It uses the authenticated user's own notification APIs to:

- Refresh unread count periodically.
- Load the most recent notifications on demand.
- Mark one notification or all notifications as read.
- Dismiss a notification.
- Route a customer order notification to the customer's order history, or return other roles to their own workspace.

Relevant APIs:

- `GET /api/notifications/unread-count`
- `GET /api/notifications?page=0&size=20`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read-all`
- `DELETE /api/notifications/{id}`

Notifications are returned by the backend for the signed-in user; the UI does not manufacture messages.

## 6. Design and motion

- Customer and shopkeeper views use the same Apniko design tokens, surface palette, typography and interaction language.
- Shopkeeper and operations navigation adapts to touch-sized mobile bottom bars with safe-area padding.
- Animation is CSS-only: short entrance transitions, restrained card lift and button press feedback, and a chart-bar reveal.
- There are no looping animations, parallax effects, gradient-heavy backgrounds or decorative generated product images.
- The shared stylesheet disables motion when the user requests `prefers-reduced-motion: reduce`.
- Interactive controls keep semantic buttons/links, visible focus and labelled navigation.

No animation library or external font dependency was added.

## 7. Data and security boundaries

- Every customer, shopkeeper, delivery and administrator screen continues to use the authenticated API and its role/ownership checks.
- Frontend route guards are navigation assistance only; backend authorization remains authoritative.
- Shop prices and stock are shop-specific; a shared catalogue product does not create shop inventory.
- The customer storefront remains text-first. Product-photo workflows are not part of Apniko. Mendeley nutrition records remain private review candidates and are never treated as sellable retail variants, barcodes or retail pack sizes.
- No Open Food Facts, retailer scraping, BigBasket catalogue data, mock products, sample stock, fabricated orders or invented analytics are introduced.
- Public registration remains CUSTOMER-only. Privileged roles are provisioned by administration.
- The existing bearer token storage in sessionStorage remains a known interim limitation: JavaScript can read it. Production authentication still requires separate review.

## 8. Verification

The current Phase 6 PR head `0f29d53cfb943cb760413b49b8abe9391ceab08d` passed all six required CI/security workflows on 23 November 2026:

| Workflow | Run | Result |
|---|---:|---|
| Frontend CI — Phase 5/6 verifiers, npm install, ESLint, TypeScript, Vitest and production build | [45216530188](https://github.com/Akprasad614/Apniko/actions/runs/45216530188) | Passed |
| Backend CI — Phase 10, Phase 2/3 tooling checks and Maven verification/tests | [45216530161](https://github.com/Akprasad614/Apniko/actions/runs/45216530161) | Passed |
| CodeQL | [45216530256](https://github.com/Akprasad614/Apniko/actions/runs/45216530256) | Passed |
| Static security scans — Semgrep and Gitleaks | [45216530197](https://github.com/Akprasad614/Apniko/actions/runs/45216530197) | Passed |
| OSV dependency scan | [45216531448](https://github.com/Akprasad614/Apniko/actions/runs/45216531448) | Passed |
| CycloneDX SBOM | [45216530874](https://github.com/Akprasad614/Apniko/actions/runs/45216530874) | Passed |

The frontend build artifact was uploaded by Frontend CI. Backend CI used its disposable MySQL service and completed Maven verification; no production database or deployment was involved.

The Phase 6 static verifier is `scripts/verify-phase6.sh`. Frontend CI runs it alongside the Phase 5 verifier, then uses the locked dependency tree to execute:

```bash
npm ci --no-audit --no-fund
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

Added unit coverage checks:
- Neighbourhood shop search by public shop name and address.
- Shopkeeper 7/30/90-day date ranges and chart percentage bounds.
- Notification destinations for each account role and notification-date formatting.

Backend Maven tests and repository security scans also run through the existing workflows when the PR is opened.

## 9. Honest completion boundary

Phase 6 software delivery is complete when the Phase 6 PR's Frontend CI, Backend CI and security checks pass. This is not a claim that the live marketplace contains products or that suppliers have supplied authorised photos. Phase 2's Mendeley seed has not been applied, and product-image publication still requires real approved imagery and verified product identity. Live operations will show the backend's real data and meaningful empty states until real shops and orders exist.

Phase 7 remains the separate end-to-end staging/accessibility milestone; it is not silently bundled into Phase 6.
