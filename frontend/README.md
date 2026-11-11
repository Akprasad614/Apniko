# LocalCart frontend

React + TypeScript + Vite application for LocalCart.

## Requirements

- Node.js 22.13 or newer in the 22.x line, or Node.js 24+ (Node 22 LTS recommended)
- npm 10.8 or newer
- LocalCart Spring Boot API running on `http://127.0.0.1:8080` for authentication calls

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

## Current scope

The landing page and authentication are wired to the current backend contract. The role routes are safe starting points for the next feature phase; catalogue, cart, checkout, shop operations and delivery flows are not implemented in this foundation milestone.
