# Apniko frontend

The Apniko customer, shopkeeper, delivery-partner and administrator interfaces are built with React, TypeScript and Vite.

## Requirements

- Node.js 22.13+ (22.x) or Node.js 24+
- npm 10.8+
- Spring Boot API available on `http://127.0.0.1:8080` for local application flows

## Run locally

From this directory:

```bash
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` requests to the Spring Boot backend on port 8080, so local development can use a same-origin API path.

## Application areas

- Customer: home and shop discovery, categories, cart, checkout, orders, account, addresses and payment-method availability.
- Shopkeeper: shop overview, order management, catalogue and inventory, performance analytics and Khatha workspace.
- Delivery partner: assigned orders and delivery lifecycle.
- Administrator: user/shop operations, catalogue review, analytics and Khatha controls.

Routes and role protection are composed in `src/app/App.tsx`. Authentication state lives in `src/auth/`; API access should use the shared client in `src/lib/api.ts`.

## Authentication

Production browser authentication uses a secure, HttpOnly, SameSite cookie with CSRF protection. The cookie is not readable by JavaScript. Do not persist authentication tokens in `localStorage` or `sessionStorage`.

The `VITE_AUTH_COOKIE_MODE` setting defaults to cookie mode. Bearer compatibility is reserved for isolated automated test fixtures and must not be enabled in a production build. Never place API keys, database credentials, payment secrets or other private values in `VITE_*` variables; Vite embeds these values in the browser bundle.

## Environment

Copy `.env.example` to `.env.local` only when a different API or map/geocoding endpoint is required. The default local setup uses the Vite `/api` proxy. Use a same-origin reverse proxy in production.

All `VITE_*` values are public configuration. Keep secrets on the backend or deployment host.

## Quality checks

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

The Playwright browser acceptance suite runs with:

```bash
npm run test:e2e
```

Install the pinned Playwright and accessibility tooling with `bash ../scripts/install-phase7-tooling.sh`, then install the required browser runtime with `npx playwright install chromium`.

## UI and catalogue conventions

The interface is mobile-first and uses the design tokens and component styles in `src/styles/` and the feature-specific stylesheets. Catalogue surfaces are text-only: do not add product images, image URLs, image placeholders or photo upload/review flows. Show only product facts returned by the backend.
