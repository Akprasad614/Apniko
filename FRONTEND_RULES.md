# Apniko frontend engineering rules

## Project structure

- Keep the React application in `frontend/`; the backend remains the Spring Boot application under `src/`.
- Keep route composition in `src/app/`, authentication in `src/auth/`, shared API and role utilities in `src/lib/`, and domain features under `src/features/`.
- Use shared components for genuinely repeated UI patterns. Keep page-specific behaviour in the relevant feature.
- Type backend request and response contracts. Do not use `any` to bypass a missing or unclear type.
- Keep network requests in the shared API layer or feature data helpers rather than presentational components.

## API and authentication

- The local Vite server proxies `/api` to `http://127.0.0.1:8080`.
- Use `apiRequest` from `src/lib/api.ts` for application API calls.
- Production browser authentication uses a secure HttpOnly SameSite cookie and CSRF protection. Do not read or persist authentication cookies or tokens through browser storage.
- Bearer compatibility is limited to isolated automated test fixtures; it must not be enabled in a production build.
- Public registration is CUSTOMER-only. Privileged roles are provisioned by an administrator.
- Backend authorization is authoritative for identity, role checks, shop ownership, order ownership, stock and price calculations. Client route guards are navigation controls, not a security boundary.
- Never place API keys, payment secrets, privileged credentials or other private values in `VITE_*` variables.
- Do not render untrusted HTML. Avoid `dangerouslySetInnerHTML`.

## User experience and accessibility

- Design mobile-first and preserve responsive behaviour at wider viewports.
- Use semantic HTML, associated labels, keyboard-accessible controls and visible focus states.
- Provide loading, empty, success and error states for asynchronous actions.
- Respect browser zoom, reduced-motion preferences and user accessibility settings.
- Keep motion purposeful and avoid animations that obstruct task completion.

## Product catalogue

- Catalogue screens are text-only. Do not add product photos, image URLs, image placeholders, upload or photo-review functionality.
- Display only verified product facts returned by the backend.
- Do not invent products, prices, pack sizes, barcodes, MRP or stock values.
- Shop selling prices, stock, availability and low-stock thresholds are shop-specific; never infer them from a master product record.

## Code quality

Before opening a frontend pull request, run:

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

Commit `package-lock.json`; CI uses `npm ci` to install the locked dependency tree.
