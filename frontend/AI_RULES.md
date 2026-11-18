# Apniko Frontend Project Rules

## Tech stack and commands

- Build and maintain the existing frontend with React 19 and TypeScript; use Vite 8 for development and production builds.
- Use React Router for client-side routing. Keep the existing route configuration in `src/app/App.tsx` and preserve all current routes and role-protected areas.
- Use Vitest for tests, ESLint for linting, and TypeScript for static checks. Existing validation scripts are `npm run lint`, `npm run typecheck`, `npm run test:ci`, and `npm run build`.
- The frontend's local `/api` proxy is configured in `vite.config.ts` to forward requests to the Spring Boot backend at `http://127.0.0.1:8080`.

## Project structure and responsibilities

- Keep route composition in `src/app/`; authentication and session state belong in `src/auth/`.
- Keep domain features in their existing areas: `src/features/customer/`, `src/features/shopkeeper/`, `src/features/admin/`, and `src/features/delivery/`. Put shared UI components in `src/components/` and shared API/role utilities in `src/lib/`.
- Use `src/lib/api.ts` as the shared API client. Preserve existing API paths, request/response shapes, authentication headers, and backend contracts; do not create a replacement backend or bypass the client for application API calls.
- Preserve the existing authentication flow, session behavior, and role permissions. Do not replace or weaken `AuthProvider`, `RequireRole`, or role-based access controls.

## UI, styling, and product data

- Use the existing CSS architecture: design tokens in `src/styles/tokens.css`, global styles in `src/styles/global.css`, and application styles in `src/styles/app.css`; follow established component and feature CSS conventions.
- Reuse existing CSS classes, design tokens, and components. Build responsive layouts mobile-first and keep the current visual language consistent.
- Do not add Tailwind CSS, shadcn/ui, Radix UI, or lucide-react unless explicitly approved. Prefer the libraries and patterns already present in the project; do not add dependencies without approval.
- Do not invent or fabricate products, prices, barcodes, stock levels, or product photographs. Render only data supplied by the existing application/backend flows and preserve existing product-photo handling.
- Work only within the existing frontend. Do not rebuild the application from scratch, replace Spring Boot, or modify backend files. Preserve existing routes and features unless the user explicitly requests a change.
