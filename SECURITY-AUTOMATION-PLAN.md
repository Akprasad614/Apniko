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

## Phase 2 — Static application security and supply-chain visibility (completed)

- Semgrep runs the `p/security-audit` ruleset and uploads SARIF findings to GitHub code scanning.
- Gitleaks scans the full Git history on pushes, pull requests, weekly schedule, and manual dispatch.
- OSV-Scanner checks Maven dependencies for known vulnerabilities and license metadata. Pull-request scans compare against `main` and fail on newly introduced vulnerabilities. The weekly/manual full scan is report-only initially.
- CycloneDX generates JSON and XML SBOM files from the Maven dependency graph.
- No scanner automatically changes application code or dependencies.

## Phase 3 — Backend authorization and business-logic regression tests (completed)

- Add customer order IDOR tests for order listing, cancellation, and status-history access.
- Add shopkeeper isolation tests for shop order listings and inventory update/restock/movement-history access.
- Add delivery-partner isolation tests for order assignment, pickup/delivery ownership, and inactive accounts.
- Add payment isolation tests to ensure a customer cannot verify another customer's payment and inactive users cannot read payment details.
- Run these tests in the existing Maven CI workflow. The tests use mocks and do not call live payment gateways or production data.

## Phase 4 — Dynamic security baseline (this change)

- Start LocalCart in GitHub Actions against a disposable MySQL 8.4 database named `localcart_dast`.
- Generate an ephemeral JWT secret and do not configure production, Razorpay, or real user credentials.
- Run the OWASP ZAP Baseline spider and passive rules against the local Swagger UI entry point.
- Upload HTML, JSON, and Markdown reports as a short-lived Actions artifact.
- Run on backend pull requests, matching pushes to `main`, weekly schedule, and manual dispatch.

Safety gates:

- This first DAST stage is passive only. It does not run ZAP active scans, Nuclei templates, destructive payloads, stress tests, or AI pentest agents.
- The target is hardcoded to the local app started in that workflow. No arbitrary external target input is accepted.
- The DAST job uses only the disposable `localcart_dast` MySQL service; it never connects to local developer or production databases.
- Initial ZAP warning findings are report-only so the baseline can be reviewed before any rule is promoted to a merge gate. Scanner execution errors still fail.
- Authenticated scans and AI-assisted testing require a later, separately reviewed plan with dedicated disposable identities and explicit manual approval.

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
- Add carefully scoped authenticated DAST and approved Nuclei templates after the passive baseline has been reviewed.
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
