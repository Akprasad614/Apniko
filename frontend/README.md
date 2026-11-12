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

- See [Brand Guidelines](./BRAND-GUIDELINES.md) for the logo-pending status, palette, typography and accessibility rules.
- See [Project Design System](../DESIGN.md) for shared art direction and page-specific design direction.
- Canonical CSS variables live in `src/styles/tokens.css`.
- Shared UI primitives live in `src/styles/brand-system.css`.
- Use `BrandMark` from `src/components/BrandMark.tsx` for the plain Apniko text wordmark. The final logo will be added when the project owner supplies it.
- New UI should use the `--apniko-*` design tokens. The old `--lc-*` names are compatibility aliases for existing screens.

## Current customer shopping experience

The customer storefront is product-first and uses the authenticated marketplace APIs:

- Browse available products across active shops before choosing a shop.
- Search in Products or Shops mode.
- Open a shop and search only that shop's inventory.
- Filter by categories and sort by name, price or shop.
- Filter listed shops/products by locality or PIN text matched against the shop address.
- Add items to the selected shop's separate basket and continue through the existing checkout, address book and order history flows.

The current Shop entity stores a free-text address, not geocoordinates. The locality filter is therefore address-text matching; it does not calculate actual distance or guarantee the nearest store. A future location phase should add validated shop coordinates and an explicit customer location/permission flow before showing distance-based rankings.

## Product images

See [Product Image Pipeline](./PRODUCT-IMAGE-PIPELINE.md) for the storage decision and provider setup.

- MySQL stores the optional product `image_url` metadata, not the image binary.
- The customer marketplace returns the exact catalogue image URL when one is assigned.
- The frontend prefers that exact image, then uses curated category photos for common grocery groups, then a local symbol fallback.
- An object-storage/CDN upload provider still needs project credentials and a backend upload-signing flow before shopkeepers can upload photographs directly from the UI.

## Mobile-first design

Apniko is designed phone-first for 320–430px viewports. Shared layout and control styles provide 44px-or-larger interactive controls, visible focus states, reduced-motion support, safe-area-aware shopkeeper navigation and no desktop tables on small screens. Tablet and desktop layouts add a workspace sidebar where space permits.
