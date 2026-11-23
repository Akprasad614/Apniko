# Phase 5 — frontend foundation and design system

**Status: completed**

**Original implementation:** [PR #10 — Add LocalCart React frontend foundation](https://github.com/Akprasad614/Apniko/pull/10), merged into `main` on 11 November 2026. The frontend remains branded as Apniko in the current source tree.

## Delivered scope

- React, TypeScript and Vite application under `frontend/`, isolated from the Spring Boot backend.
- Typed client routing with customer, shopkeeper, delivery-partner and administrator route groups.
- Shared Apniko design tokens for colour, typography, spacing, radii and page width.
- Responsive public landing page and reusable wordmark, button and labelled-field components.
- Customer login and customer-only public registration, connected to the backend authentication contract:
  - `POST /api/auth/login`
  - `POST /api/auth/register`
- Authentication state provider with session restore, sign-in, customer sign-up and sign-out.
- Role-aware navigation guards. They improve the user experience but are not a replacement for server-side authorization.
- Typed API request helper with JSON headers, bearer token support, normalized backend/network errors and HTTP status preservation.
- Vite `/api` development proxy to the local Spring Boot backend.
- ESLint, strict TypeScript checks and Vitest unit tests.
- GitHub Actions frontend CI using the committed lockfile and `npm ci`, then lint, typecheck, unit tests and production build.
- Dependabot npm updates for `/frontend`.
- Product, design and frontend engineering standards in `PRODUCT.md`, `DESIGN.md` and `FRONTEND_RULES.md`.

## Validation evidence

The original Phase 5 pull-request head `44c794e06f3d3d5131840fdd11c8308f63678e9f` completed the following checks successfully:

| Check | Workflow run | Result |
|---|---:|---|
| Frontend CI — configuration, npm install, ESLint, TypeScript, Vitest and production build | [42993829874](https://github.com/Akprasad614/Apniko/actions/runs/42993829874) | Passed |
| Backend CI | [42993829792](https://github.com/Akprasad614/Apniko/actions/runs/42993829792) | Passed |
| CodeQL | [42993829865](https://github.com/Akprasad614/Apniko/actions/runs/42993829865) | Passed |
| Static security scans | [42993829765](https://github.com/Akprasad614/Apniko/actions/runs/42993829765) | Passed |
| OSV dependency scan | [42993830357](https://github.com/Akprasad614/Apniko/actions/runs/42993830357) | Passed |
| CycloneDX SBOM | [42993829882](https://github.com/Akprasad614/Apniko/actions/runs/42993829882) | Passed |

The Frontend CI job explicitly completed the Phase 5 configuration verifier, locked dependency installation, ESLint, TypeScript checks, Vitest tests, production build and build-artifact upload.

The repository guardrail is `scripts/verify-phase5.sh`. Frontend CI runs it before installing dependencies and running the frontend checks.

## Security and scope boundaries

- The current authentication foundation stores the returned bearer token in `sessionStorage`. This reduces persistence compared with `localStorage`, but JavaScript can still read it; it is not an XSS defence. A production authentication design should prefer secure HttpOnly SameSite cookies or another separately reviewed approach.
- Client-side role guards are navigation controls only. Backend authorization remains authoritative for roles, ownership, inventory, pricing, orders and payments.
- Public registration submits `role: CUSTOMER` only. Shopkeeper, delivery-partner and administrator accounts are provisioned by Apniko administration.
- Never add secrets to `VITE_*` environment variables; those values are exposed in browser bundles.
- Phase 5 establishes the frontend foundation; it does not certify production authentication or replace the independently scoped customer, shopkeeper, delivery and admin feature milestones.

## Completion checklist

- [x] React, TypeScript and Vite foundation exists under `frontend/`.
- [x] Apniko design direction and central design tokens are documented and implemented.
- [x] Responsive public landing, shared controls and role-aware route shells exist.
- [x] Login and customer-only registration use the verified backend auth endpoints.
- [x] Typed API helper and local `/api` proxy exist.
- [x] ESLint, strict TypeScript, Vitest and production build checks exist.
- [x] Frontend CI runs the complete quality gate and stores a build artifact.
- [x] npm Dependabot coverage is configured for `/frontend`.
- [x] The original Phase 5 CI/security suite passed.

**Exit result:** Phase 5 foundation is complete. Continue with the next roadmap milestone without rebuilding this foundation from scratch.
