# Apniko frontend engineering rules

## Structure

- Keep the frontend in `frontend/`; do not move or rewrite the Spring Boot backend.
- Group by app, auth, components, lib, pages and styles. Promote repeated UI patterns into shared components only when they are actually reused.
- Type backend request and response shapes. Do not use `any` to avoid understanding a DTO.
- Keep API access out of presentational components.

## API and authentication

- The local Vite server proxies `/api` to `http://127.0.0.1:8080`.
- Use `apiRequest` for HTTP calls so errors and bearer headers are handled consistently.
- Public registration is CUSTOMER-only. The UI must not offer public registration for privileged roles.
- The backend is the authority for identity, role checks, shop ownership, order ownership, stock and price calculations. Client-side guards are navigation only.
- The current backend returns a bearer token in the authentication response. This foundation keeps it in `sessionStorage`, not `localStorage`, to limit persistence. `sessionStorage` is still readable by JavaScript and is not an XSS defence. A production authentication design should prefer secure, HttpOnly, SameSite cookies or another reviewed token-handling design.
- Never place API secrets, payment secrets or privileged credentials in `VITE_*` variables; Vite variables are public in the browser bundle.
- Do not render untrusted HTML. Avoid `dangerouslySetInnerHTML`.

## Accessibility and interaction

- Use semantic HTML and real labels.
- Ensure keyboard navigation and visible focus states.
- Provide loading, empty, success and error states for asynchronous actions.
- Avoid disabling browser zoom or overriding user accessibility settings.
- Honour reduced-motion preferences.
- Test responsive layouts with narrow mobile and wide desktop viewports.

## Code quality

Before opening a frontend pull request, run:

```bash
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

Commit `package-lock.json`; CI uses `npm ci` and must not resolve a fresh dependency tree on every run.
