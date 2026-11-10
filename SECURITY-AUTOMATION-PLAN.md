# LocalCart Development and Security Automation Roadmap

This roadmap is maintained with the repository. Automation is introduced in small, verifiable stages. Security scanners must be scoped to LocalCart code or explicitly authorized LocalCart test/staging environments.

## Phase 0 — Baseline and safety (completed)

- Preserve the Phase 10 backend, Postman collections, OpenAPI configuration, and isolated test database scripts.
- Keep the main local database separate from test and CI databases.
- Confirm `main` is the default branch and keep feature work on dedicated branches.
- Never commit local credentials, private Postman environments, payment keys, or production secrets.

## Phase 1 — CI and dependency-maintenance foundation (completed)

- GitHub Actions CI provisions a fresh MySQL 8.4 service and runs the Phase 10 static verifier plus `./mvnw verify` on pushes and pull requests.
- CI generates a one-run JWT secret. The test database and root password are disposable CI-only values.
- CodeQL Java analysis runs on pushes, pull requests, weekly schedule, and manual dispatch.
- Dependabot checks Maven and GitHub Actions dependencies weekly and opens update pull requests.
- CI has read-only repository permission; CodeQL has only the additional permissions required to upload code-scanning results.

## Phase 2 — Static application security and supply-chain visibility (this change)

- Semgrep runs the `p/security-audit` ruleset and uploads SARIF findings to GitHub code scanning. Initial findings are for review and triage; this workflow does not automatically fix source code.
- Gitleaks scans the full Git history on pushes, pull requests, weekly schedule, and manual dispatch. A confirmed secret must be revoked/rotated; removing it from the latest file alone is not sufficient.
- OSV-Scanner checks Maven dependencies for known vulnerabilities and license metadata. Pull-request scans compare against `main` and fail on newly introduced vulnerabilities. The weekly/manual full scan is report-only initially so the existing dependency baseline can be reviewed before setting a blocking policy.
- CycloneDX generates JSON and XML SBOM files from the Maven dependency graph. The files are retained as a GitHub Actions artifact for 30 days.
- Reports are uploaded to GitHub code scanning or workflow artifacts; no scanner is allowed to change application code or dependencies automatically.

Safety gates:

- Workflows use read-only repository access except for narrowly scoped security-results upload permission.
- No production credentials or local database secrets are required.
- No active penetration tests, destructive payloads, or external target scans run in this phase.

## Phase 3 — Backend authorization and business-logic regression tests

Planned automated tests:

- Customer-to-customer order and cart isolation (IDOR/BOLA).
- Shopkeeper isolation across shops, products, inventory, and orders.
- Delivery-partner access limited to assigned deliveries.
- Admin-only operation protection.
- Server-side price, quantity, discount, and order-total integrity.
- Inventory race conditions and overselling.
- Payment signature validation, webhook replay/idempotency, and status transitions.
- JWT expiry, invalid signature, missing token, and role-claim handling.

## Phase 4 — Dynamic security testing on isolated staging

Planned automations:

- OWASP ZAP baseline scan on the dedicated staging URL.
- Nuclei checks using an approved template set and severity allowlist.
- Authenticated scans only with dedicated test accounts and narrowly scoped routes.
- Shannon or another AI pentest agent only through explicit manual approval against an isolated staging target.

Safety gates:

- No scans against production, third-party sites, or arbitrary user-supplied targets.
- No destructive templates, stress tests, denial-of-service tests, or data-deletion payloads.
- Active scans do not run automatically on ordinary pull requests.

## Phase 5 — Frontend foundation and design system

Planned work:

- Add React, TypeScript, and Vite under a dedicated `frontend/` directory.
- Add `PRODUCT.md`, `DESIGN.md`, and `FRONTEND_RULES.md`.
- Apply the agreed design references (Anthropic Frontend Design, Taste, UI/UX Pro Max, Vercel Web Interface Guidelines, and Impeccable) as project instructions and review criteria.
- Establish reusable design tokens, components, routes, API client, and role-aware layouts.

## Phase 6 — Frontend quality automation

Planned automations:

- TypeScript type-check and production build.
- ESLint and unit/component tests.
- Playwright browser tests for core customer journeys.
- Accessibility checks using axe-core.
- Screenshot artifacts and visual-regression review for key pages.

## Phase 7 — Role-based application delivery

Build and test incrementally:

- Customer storefront, search, cart, checkout, payment, and order tracking.
- Shopkeeper product/inventory/order management and analytics.
- Delivery-partner task list and delivery lifecycle.
- Admin operations where required.

Every feature must pass relevant backend tests, frontend checks, and authorization tests before merge.

## Phase 8 — Full end-to-end staging verification

Planned automations:

- Deploy pull-request previews or a dedicated staging environment.
- Run API and browser end-to-end flows with seeded test data.
- Run ZAP/Nuclei staging checks and collect reports.
- Verify that test jobs never connect to the main development or production database.

## Phase 9 — Production readiness and release controls

Planned work:

- Configure protected `main` branch rules and required checks after the workflows have passed at least once.
- Require review for security-sensitive changes.
- Configure production secrets in the hosting provider, not in the repository.
- Add HTTPS, security headers, restricted database access, monitoring, backups, and rollback procedures.
- Keep a release checklist and a security findings register.

## Completion policy

A green scanner result is not proof that the application has no vulnerabilities. Findings must be triaged, important fixes must be retested, and residual risks must be documented. Automated agents may propose changes, but security-sensitive fixes require human review before merge.
