# Apniko Development and Security Automation Roadmap

This roadmap is maintained with the repository. Automation is introduced in small, verifiable stages. Security scanners must be scoped to Apniko code or explicitly authorized Apniko test/staging environments.

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

## Phase 4 — Dynamic security baseline (completed)

- Start Apniko in GitHub Actions against a disposable MySQL 8.4 database named `localcart_dast`.
- Generate and mask an ephemeral JWT secret; do not configure production, Razorpay, or real user credentials.
- Run the OWASP ZAP Baseline spider and passive rules against the local Swagger UI entry point.
- Require HTML, JSON, and Markdown reports and upload them as a short-lived Actions artifact.
- Run on backend pull requests, matching pushes to `main`, weekly schedule, and manual dispatch.
- Initial ZAP findings are reviewed from the generated report; a completed scan is not treated as proof of zero vulnerabilities.

Safety gates:

- This DAST stage is passive only. It does not run ZAP active scans, Nuclei templates, destructive payloads, stress tests, or AI pentest agents.
- The target is hardcoded to the local app started in that workflow. No arbitrary external target input is accepted.
- The DAST job uses only the disposable `localcart_dast` MySQL service; it never connects to local developer or production databases.
- ZAP warning findings remain visible in reports and can be promoted to a blocking policy after review.

## Phase 5 — Frontend foundation and design system (this change)

- Add React, TypeScript and Vite under `frontend/`, separate from the Spring Boot backend.
- Add a deliberate Apniko visual direction, central design tokens, reusable controls, responsive public landing page and role-aware route shells.
- Wire customer registration and sign-in to the verified backend contract. Public registration remains CUSTOMER-only; privileged account types are provisioned by Apniko administration.
- Add a typed API request helper, local `/api` development proxy, ESLint, TypeScript checks and Vitest unit tests.
- Add GitHub Actions frontend CI for lint, typecheck, tests and production build.
- Add Dependabot coverage for npm dependencies under `/frontend`.

Authentication note:

- The existing backend returns a bearer token in the JSON authentication response. The first frontend foundation keeps it in `sessionStorage` rather than `localStorage` to limit persistence. This is not an XSS defence because JavaScript can still read it. A production auth design should prefer secure HttpOnly SameSite cookies or another reviewed approach.
- Frontend route guards are navigation aids only. All roles, ownership, inventory, pricing, order and payment decisions must continue to be enforced by the backend.

## Phase 6 — Role-based application delivery

Build and test incrementally:

- Customer storefront, search, basket, checkout, payment and order tracking.
- Shopkeeper product/inventory/order management and analytics.
- Delivery-partner task list and delivery lifecycle.
- Admin operations where required.

Every feature must be based on the current backend DTOs and endpoint behaviour. Do not create fake stock counts, prices, delivery ETAs or analytics.

## Phase 7 — Full end-to-end staging verification

- Add Playwright browser journeys for customer, shopkeeper and delivery workflows.
- Add accessibility checks using axe-core and review responsive screenshots for key pages.
- Deploy pull-request previews or a dedicated staging environment.
- Run API and browser end-to-end flows with seeded test data.
- Add carefully scoped authenticated DAST and approved Nuclei templates only after a separately reviewed plan with dedicated disposable identities and explicit approval.
- Verify that test jobs never connect to the main development or production database.

## Phase 8 — Production readiness and release controls

- Configure protected `main` branch rules and required checks after the workflows have passed at least once.
- Require review for security-sensitive changes.
- Configure production secrets in the hosting provider, not in the repository.
- Add HTTPS, content security policy, other security headers, restricted database access, monitoring, backups and rollback procedures.
- Replace the temporary browser token storage design with the reviewed production authentication approach.
- Keep a release checklist and a security findings register.

## Completion policy

A green scanner result is not proof that the application has no vulnerabilities. Findings must be triaged, important fixes must be retested, and residual risks must be documented. Automated agents may propose changes, but security-sensitive fixes require human review before merge.
