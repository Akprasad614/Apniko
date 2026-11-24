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

## Phase 5 — Frontend foundation and design system (completed)

Implemented in [PR #10](https://github.com/Akprasad614/Apniko/pull/10), merged into `main` on 11 November 2026.

- [x] React, TypeScript and Vite under `frontend/`, separate from the Spring Boot backend.
- [x] Apniko visual direction, central design tokens, reusable controls, responsive public landing page and role-aware route shells.
- [x] Customer registration and sign-in wired to the verified backend contract. Public registration is CUSTOMER-only; privileged account types are provisioned by Apniko administration.
- [x] Typed API request helper, local `/api` development proxy, ESLint, TypeScript checks and Vitest unit tests.
- [x] GitHub Actions frontend CI for lint, typecheck, tests and production build.
- [x] Dependabot coverage for npm dependencies under `/frontend`.
- [x] Original frontend and security CI checks passed; see [run 42993829874](https://github.com/Akprasad614/Apniko/actions/runs/42993829874) and the completion evidence in `PHASE5-FRONTEND-FOUNDATION.md`.

Authentication and security boundaries:

- The existing backend returns a bearer token in the JSON authentication response. The first frontend foundation keeps it in `sessionStorage` rather than `localStorage` to limit persistence. This is not an XSS defence because JavaScript can still read it. A production auth design should prefer secure HttpOnly SameSite cookies or another reviewed approach.
- Frontend route guards are navigation aids only. All roles, ownership, inventory, pricing, order and payment decisions must continue to be enforced by the backend.

## Phase 6 — Role-based application delivery (completed)

Implemented on the dedicated branch `feature/phase-6-role-based-app-delivery`; see `PHASE6-ROLE-BASED-DELIVERY.md`.

- [x] Customer storefront, shop search, product search/category/price filters, shop-specific basket and stock-aware quantity updates.
- [x] Customer checkout, COD/Razorpay method availability, payment initiation/verification, order history, status history and permitted cancellation.
- [x] Shopkeeper mobile workspace with the same Apniko design tokens and navigation language as the customer application.
- [x] Shopkeeper order acceptance, fulfilment selection, preparation/ready/delivery transitions, rejection/cancellation rules and order history.
- [x] Shopkeeper catalogue selection, shop-specific inventory, pricing/availability, restock, low-stock thresholds and movement audit history.
- [x] Shopkeeper performance view backed by the actual 7/30/90-day analytics endpoints; account-wide totals are labelled separately from selected-shop product rankings.
- [x] Explainable shopkeeper stock guidance backed by order history and current inventory using the documented weighted-moving-average baseline.
- [x] Delivery-partner available/assigned/completed workflows, including accept, pickup and delivery actions.
- [x] Administrator overview, staff provisioning/activation, shop ownership/activation and text-only catalogue administration. Product-photo moderation is excluded by Phase 4.
- [x] Shared authenticated notifications across customer, shopkeeper, delivery and administrator workspaces, including unread count, mark read, mark all read and dismiss.
- [x] Responsive mobile bottom navigation, shared interaction styling and restrained CSS motion with `prefers-reduced-motion` support.
- [x] Phase 6 static verifier and unit tests for shop search, analytics date/scale helpers and notification routing/date formatting.

Every feature uses existing API response data and backend authorization. No demo products, synthetic inventory, fabricated prices/sales/ETAs or generated packshots are used. Browser-based end-to-end and staging verification remain in Phase 7.

## Phase 7 — Full end-to-end staging verification (completed)

Implemented and accepted on `feature/phase-7-e2e-accessibility-staging`; see `PHASE7-STAGING-VERIFICATION.md`.

Final integrated acceptance ran on 24 November 2026 against the current Phase 3–4 text-only development base, on head `ec37d51628233b0c81ed367e3e552fed1ec0295d`:

- [x] Real Spring Boot API and Vite application run against a disposable MySQL 8.4 service database named `localcart_phase7`.
- [x] API-created ephemeral fixtures exercise customer checkout, shopkeeper acceptance/preparation/readiness, delivery-partner acceptance/pickup/delivery, and customer order-history confirmation.
- [x] A shopkeeper request for administrator-only user data is rejected with HTTP 403.
- [x] axe-core WCAG 2.0/2.1 A/AA checks pass on customer, shopkeeper, delivery and administrator workspaces; critical and serious violations fail the job.
- [x] 390 × 844 mobile overflow checks pass for all four roles; nine desktop/mobile/order screenshots are retained.
- [x] Customer catalogue product cards are text-only; Playwright asserts no image, picture or category-art placeholder is rendered.
- [x] Phase 4 text-only source verifier runs inside the dedicated Phase 7 pull-request workflow against the integrated development base.
- [x] Frontend lint, TypeScript, unit tests and production build pass.
- [x] Backend Maven verification and tests pass against disposable Phase 7 MySQL.
- [x] CodeQL, Semgrep/Gitleaks, OSV dependency scan and CycloneDX SBOM pass.
- [x] Playwright report, test attachments and nine screenshots are retained as a 14-day artifact and were visually reviewed.

**Final verification evidence**

- Dedicated browser/accessibility/staging workflow: [run 45426389667](https://github.com/Akprasad614/Apniko/actions/runs/45426389667) — passed.
- Frontend CI: [run 45426389402](https://github.com/Akprasad614/Apniko/actions/runs/45426389402) — passed.
- Backend CI: [run 45426389498](https://github.com/Akprasad614/Apniko/actions/runs/45426389498) — passed.
- CodeQL: [run 45426389474](https://github.com/Akprasad614/Apniko/actions/runs/45426389474) — passed.
- Static security: [run 45426389462](https://github.com/Akprasad614/Apniko/actions/runs/45426389462) — passed.
- CycloneDX SBOM: [run 45426389569](https://github.com/Akprasad614/Apniko/actions/runs/45426389569) — passed.
- OSV dependency scan: [run 45426390593](https://github.com/Akprasad614/Apniko/actions/runs/45426390593) — passed.
- Retained artifact: [apniko-phase7-e2e-35](https://github.com/Akprasad614/Apniko/actions/runs/45426389667/artifacts/14936369137) — nine screenshots, HTML report and test attachments; retention ends 8 December 2026.

**Deliberate scope boundaries — not Phase 7 blockers**

- Staging is ephemeral and isolated inside GitHub Actions. No persistent public preview was configured because no Vercel team/project is linked; no public deployment was performed.
- Razorpay is not contacted; the test order uses COD. No production database, live customer account or supplier catalogue is used.
- Authenticated active DAST, Nuclei templates and destructive tests remain disabled until a separately reviewed target/identity plan and explicit authorization exist.
- The existing JavaScript-readable `sessionStorage` bearer-token approach remains a production-authentication limitation; Phase 7 does not claim to have solved it.

**Phase 7 completion gate: satisfied.** The integrated browser journey, text-only product contract, accessibility checks, responsive tests and paired CI/security workflows all passed. Phase 7 is complete; Phase 8 work has not started.

## Phase 8 — Production readiness and release controls (implementation integrated)

Merged to the development branch through
[PR #70](https://github.com/Akprasad614/Apniko/pull/70). No change was made to
main.

- [x] Production browser authentication moved to HttpOnly/Secure/SameSite
  cookies with CSRF-protected write requests and session restoration/logout.
- [x] Strong password validation, bounded authentication rate limiting,
  Retry-After throttling and payment row locks.
- [x] Hardened production Spring profile, safe health probes and external
  database/JWT secret requirements.
- [x] Multi-stage Docker images, non-root backend, private backend/database
  network, Nginx same-origin API proxy and Caddy HTTPS/security headers.
- [x] TLS-verified backup and explicitly guarded restore utilities.
- [x] Full Phase 8, Phase 7, backend, frontend, CodeQL, Semgrep/Gitleaks, OSV,
  SBOM, strict passive DAST and action-pin acceptance passed.
- [ ] Generate and review a versioned Flyway baseline from the real,
  known-good, backed-up development schema.
- [ ] Configure a real production host/domain/DNS and ingress.
- [ ] Provision production DB/JWT secrets in the host secret manager.
- [ ] Apply and test the GitHub main branch ruleset.
- [ ] Configure external monitoring, encrypted offsite backup retention and
  complete a successful restore drill.

**Implementation and CI acceptance are complete; production release remains
blocked on the five owner-controlled items above.**

## Completion policy

A green scanner result is not proof that the application has no vulnerabilities. Findings must be triaged, important fixes must be retested, and residual risks must be documented. Automated agents may propose changes, but security-sensitive fixes require human review before merge.
