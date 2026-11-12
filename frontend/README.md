# Apniko frontend

React + TypeScript + Vite application for Apniko.

## Requirements

- Node.js 22.13 or newer in the 22.x line, or Node.js 24+ (Node 22 LTS recommended)
- npm 10.8 or newer
- Apniko Spring Boot API running on `http://127.0.0.1:8080` for authentication calls

## Start locally

```bash
npm install
npm run dev
```

Open `http://127.0.0.1:5173`.

Vite proxies `/api` requests to the Spring Boot backend at `http://127.0.0.1:8080`. This avoids a local cross-origin setup. Change `vite.config.ts` if your backend uses a different local address.

## Checks

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

## Environment

Copy `.env.example` to `.env.local` only if the frontend must call a different API origin. Prefer a same-origin reverse proxy in production. Any `VITE_*` value is public browser code; never put secrets in it.

## Brand and design system

- See [Brand Guidelines](./BRAND-GUIDELINES.md) for logo usage, palette, typography and accessibility rules.
- See [Project Design System](../DESIGN.md) for shared art direction and page-specific design direction.
- Canonical CSS variables live in `src/styles/tokens.css`.
- Shared UI primitives live in `src/styles/brand-system.css`.
- Use `BrandMark` from `src/components/BrandMark.tsx` for the Apniko mark and wordmark. Do not recreate the logo ad hoc in individual pages.
- Logo artwork is under `public/brand/`; the vector favicon is `public/favicon.svg`.

New UI should use the `--apniko-*` design tokens. The old `--lc-*` names are kept as compatibility aliases for already-built screens.

## Current scope

The landing page, customer authentication and customer storefront are wired to the current backend contract. The customer storefront uses live marketplace, basket and shop data.

Shopkeeper Workspace V1 is available at `/shopkeeper` for authenticated shopkeeper accounts. It uses the existing backend APIs to:

- Select or create the shopkeeper's own shop.
- View inventory and low-stock items.
- Add existing products to a shop, update price, stock and availability, restock, and set stock alerts.
- View shop orders and perform only the backend-supported status changes: placed → accepted → preparing → ready.
- Cancel eligible orders with confirmation.

No dashboard metrics, revenue figures, product catalogue entries or order data are invented by the frontend. The backend remains the source of truth.

## Mobile-first design

Apniko is designed phone-first for 320–430px viewports. Shared layout and control styles provide 44px-or-larger interactive controls, visible focus states, reduced-motion support, safe-area-aware shopkeeper navigation and no desktop tables on small screens. Tablet and desktop layouts add a workspace sidebar where space permits.
