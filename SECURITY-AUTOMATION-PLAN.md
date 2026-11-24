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
- [x] Administrator overview, staff provisioning/activation, shop ownership/activation, catalogue administration, product-photo and catalogue moderation.
- [x] Shared authenticated notifications across customer, shopkeeper, delivery and administrator workspaces, including unread count, mark read, mark all read and dismiss.
- [x] Responsive mobile bottom navigation, shared interaction styling and restrained CSS motion with `prefers-reduced-motion` support.
- [x] Phase 6 static verifier and unit tests for shop search, analytics date/scale helpers and notification routing/date formatting.

Every feature uses existing API response data and backend authorization. No demo products, synthetic inventory, fabricated prices/sales/ETAs or generated packshots are used. Browser-based end-to-end and staging verification remain in Phase 7.

## Phase 7 — Full end-to-end staging verification (original branch passed; consolidated revalidation underway)

Implemented and accepted on `feature/phase-7-e2e-accessibility-staging`; see `PHASE7-STAGING-VERIFICATION.md`.

Final acceptance was run on branch head `768fad50bbd7ce56204e56c8ced1d6161d4cf407` on 23 November 2026:

- [x] Playwright starts the real Spring Boot API and Vite application against a disposable MySQL 8.4 database named `localcart_phase7`.
- [x] API-created CI-only fixtures exercise customer checkout, shopkeeper acceptance/preparation/readiness, delivery-partner acceptance/pickup/delivery, and customer order-history confirmation.
- [x] A shopkeeper request for administrator-only user data is rejected with HTTP 403.
- [x] axe-core WCAG 2.0/2.1 A/AA checks pass on customer, shopkeeper, delivery and administrator workspaces with no critical or serious violations.
- [x] 390 × 844 mobile overflow checks pass for all four roles; desktop and mobile screenshots are retained for each role, plus delivered customer order history.
- [x] Browser exceptions, Playwright traces and the HTML report are retained with the screenshot artifact.
- [x] Frontend lint, TypeScript, unit tests and production build pass on the final Phase 7 head.
- [x] Backend Maven verification and tests pass against the disposable Phase 7 MySQL service.
- [x] The Phase 7 static verifier runs in frontend CI, and the dedicated browser/accessibility workflow passes on the final branch head.
- [x] The final artifact was downloaded and its nine desktop/mobile/order screenshots were visually reviewed during closeout.

**Final verification evidence**

- Dedicated browser/accessibility/staging workflow: [run 45235967198](https://github.com/Akprasad614/Apniko/actions/runs/45235967198) — passed.
- Frontend CI: [run 45235967217](https://github.com/Akprasad614/Apniko/actions/runs/45235967217) — passed.
- Retained artifact: [apniko-phase7-e2e-22](https://github.com/Akprasad614/Apniko/actions/runs/45235967198/artifacts/14849856177) — nine screenshots, Playwright HTML report and test evidence; retention ends 7 December 2026.

**Deliberate scope boundaries — not Phase 7 blockers**

- The staging environment is ephemeral and isolated inside GitHub Actions. No persistent public preview was configured because this repository has no linked Vercel team/project; a public deployment is not required for this acceptance milestone.
- Authenticated active DAST, Nuclei templates and destructive tests remain disabled until a separately reviewed target/identity plan and explicit authorization exist. The completed Phase 7 workflow does not run those tests.
- The workflow uses only localhost and the disposable `localcart_phase7` database. It does not contact production, real users, Razorpay or a supplier catalogue.
- The existing JavaScript-readable `sessionStorage` bearer-token approach remains a known production-authentication limitation and is not represented as solved by this test harness.

The Phase 7 completion gate is satisfied: the dedicated workflow passed on the final branch head and the uploaded axe/screenshots evidence was reviewed. The phase is complete on its feature branch; no pull request has been merged and no deployment has been performed.

## Phase 8 — Production readiness and release controls

Implementation is on `feature/phase-8-production-readiness`; see `PHASE8-PRODUCTION-READINESS.md`, `MAIN-BRANCH-PROTECTION.md` and `OPERATIONS-RUNBOOK.md`.

**Repository implementation**
- [x] Replace persistent browser JWT storage with HttpOnly, Secure, SameSite=Lax session cookies in the production profile.
- [x] Add CSRF token bootstrap and `X-XSRF-TOKEN` handling for mutating browser requests; keep the signed Razorpay webhook on its provider-signature validation path.
- [x] Add cookie-backed session restoration and logout; production no longer accepts the browser's JavaScript-readable bearer token.
- [x] Keep bearer-token mode explicitly configurable for isolated CI fixtures and non-production API regression tests.
- [x] Require a 32-byte JWT secret and configure the token lifetime.
- [x] Add a production Spring profile with external DB/JWT secrets, TLS JDBC configuration, bounded connection pool, Hibernate `validate`, safe error settings, disabled SQL logs and disabled public Swagger/OpenAPI.
- [x] Add sanitized Actuator liveness/readiness probes.
- [x] Add multi-stage backend/frontend Dockerfiles, same-origin Nginx API proxy, Caddy automatic HTTPS and baseline CSP/HSTS/security headers.
- [x] Keep backend and Actuator off public host ports; mount DB/JWT secrets as Docker secrets.
- [x] Add TLS-verified database backup, checksum and explicit-confirmation restore scripts.
- [x] Add immutable workflow pin verification.
- [x] Pin third-party GitHub Actions and reusable workflows to full commit SHAs; add a verifier workflow to prevent mutable references.
- [x] Add a Phase 8 CI gate for production-contract checks, Docker Compose validation, Maven, frontend checks, Playwright order lifecycle, axe and mobile evidence.
- [x] Add branch-protection instructions, deployment environment example and operations runbook.

**External release gates — not applied or fabricated by this code change**
- [ ] Apply and verify the `main` branch ruleset from GitHub repository Settings. The available GitHub file/PR actions do not expose ruleset administration.
- [ ] Select a hosting provider/domain, configure DNS and provision a managed TLS-enabled MySQL database.
- [ ] Populate `.env.production`, `secrets/db_password.txt` and `secrets/jwt_secret.txt` on the deployment host. No real values belong in GitHub source or this chat.
- [ ] Create and review a versioned schema migration baseline. Existing development relies on Hibernate `update`; production deliberately uses `validate` and must not bootstrap an empty database automatically.
- [ ] Configure an external uptime/logging/alerting provider and encrypted offsite backup retention; complete and record a restore drill.

The authentication limiter is in-memory and intended for one backend replica; multiple replicas require a shared atomic limiter or trusted gateway. Phase 8 repository code preparation is complete when the dedicated Phase 8 workflow passes. A public production release remains gated on the owner-controlled items above; no deployment or production data change is part of this milestone.

## Completion policy

A green scanner result is not proof that the application has no vulnerabilities. Findings must be triaged, important fixes must be retested, and residual risks must be documented. Automated agents may propose changes, but security-sensitive fixes require human review before merge.
