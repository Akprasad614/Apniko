# Phase 7 — end-to-end staging verification

**Status: Phase 7 complete — integrated end-to-end, accessibility, responsive and security acceptance passed.**
**Accepted feature branch head:** `ec37d51628233b0c81ed367e3e552fed1ec0295d`
**Integration branch:** `feature/phase-7-e2e-accessibility-staging`
**Target development branch:** `chore/phase-0-foundation-cleanup`
**Parent milestone:** Phase 6 role-based application delivery

## Verification record

Final integrated acceptance ran on **24 November 2026** against the Phase 3–4 development base:

- Dedicated browser/accessibility/staging workflow: [run #45426389667](https://github.com/Akprasad614/Apniko/actions/runs/45426389667) — **passed**.
- Frontend CI: [run #45426389402](https://github.com/Akprasad614/Apniko/actions/runs/45426389402) — **passed**.
- Backend CI: [run #45426389498](https://github.com/Akprasad614/Apniko/actions/runs/45426389498) — **passed**.
- CodeQL: [run #45426389474](https://github.com/Akprasad614/Apniko/actions/runs/45426389474) — **passed**.
- Static security (Semgrep/Gitleaks): [run #45426389462](https://github.com/Akprasad614/Apniko/actions/runs/45426389462) — **passed**.
- CycloneDX SBOM: [run #45426389569](https://github.com/Akprasad614/Apniko/actions/runs/45426389569) — **passed**.
- OSV dependency scan: [run #45426390593](https://github.com/Akprasad614/Apniko/actions/runs/45426390593) — **passed**.
- Retained artifact: [apniko-phase7-e2e-35](https://github.com/Akprasad614/Apniko/actions/runs/45426389667/artifacts/14936369137) — Playwright report, traces/test attachments and nine desktop/mobile/order screenshots; retention ends **8 December 2026**.

The dedicated integrated workflow passed the Phase 7 safety verifier, Phase 2/3 catalogue contracts, **Phase 4 text-only catalogue verifier**, Maven verification against disposable MySQL, locked frontend/browser tooling, Chromium installation and the complete Playwright journey. The API lifecycle, 403 role isolation, axe checks for all four roles and mobile overflow checks passed. The E2E browser test also asserts that product cards contain no `img`, `picture` or category-art placeholder.

I downloaded and visually reviewed the nine retained screenshots. Customer catalogue cards display only product text and shop data; no product photographs or placeholder artwork appear in the catalogue cards.

## What the Phase 7 workflow runs

The workflow starts the actual Spring Boot application and Vite frontend on a GitHub-hosted runner. The backend connects only to a new MySQL 8.4 service database named localcart_phase7. No developer database, production database, live user account, payment gateway or supplier catalogue is used.

The test creates disposable fixtures through authenticated APIs:

- A one-run administrator bootstrap identity.
- A shopkeeper and delivery-partner account provisioned by the administrator.
- A shop owned by the test shopkeeper.
- One clearly labelled Phase 7 test product and shop-specific stock.
- A customer and a disposable delivery address.

The fixtures exist only in the ephemeral CI database, discarded with the job.

## Browser acceptance journey

A serial Playwright browser journey exercises the real API-backed lifecycle:

1. Customer signs in, discovers the test shop/product, adds the product and places a Cash on Delivery order.
2. Shopkeeper selects Apniko delivery-partner fulfilment, accepts the order, prepares it and marks it ready.
3. Delivery partner accepts the ready order, confirms pickup and marks it delivered.
4. Customer returns to order history and verifies delivery completion.
5. Administrator signs in and opens the protected operations workspace.
6. A cross-role API request must be rejected with HTTP 403.

Razorpay is not called. No live payment provider or customer data is used.

## Accessibility and responsive evidence

- axe-core checks WCAG 2.0/2.1 A/AA rules on customer, shopkeeper, delivery and administrator workspaces.
- Critical and serious axe violations fail the browser job.
- Horizontal overflow is checked at a 390 × 844 mobile viewport for each role.
- Desktop and mobile screenshots are captured for all four roles plus customer order history after delivery.
- Uncaught browser exceptions fail the journey.
- Playwright reports, traces and screenshots are retained as a 14-day Actions artifact.

## Security and completion boundaries

- Test credentials and JWT secrets are generated at CI runtime and never committed.
- Browser/API tests target localhost only; the workflow accepts no arbitrary external target.
- The workflow has read-only repository permissions and uses no deployment secrets.
- Authenticated active DAST, Nuclei templates and destructive tests remain disabled pending a separately reviewed target/identity plan and explicit approval.
- No persistent Vercel preview is configured because Vercel discovery returned no linked teams/projects. The workflow instead provides an ephemeral full-stack staging environment inside each CI run.
- The existing sessionStorage bearer-token design remains a production-authentication risk; this test harness does not resolve it.

## Local run

Start an empty, disposable MySQL database named localcart_phase7. Do not point this harness at the developer or production database.

From the repository root:

    chmod +x ./mvnw
    bash scripts/verify-phase7.sh
    cd frontend
    npm ci
    bash ../scripts/install-phase7-tooling.sh
    npx playwright install chromium
    export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/localcart_phase7?allowPublicKeyRetrieval=true&useSSL=false'
    export DB_PASSWORD='<disposable-test-password>'
    export JWT_SECRET='<one-run-random-secret>'
    export ADMIN_BOOTSTRAP_EMAIL='phase7-admin@local.test'
    export ADMIN_BOOTSTRAP_PASSWORD='A7!phase7-<random-suffix>z'
    export PHASE7_TEST_PASSWORD="$ADMIN_BOOTSTRAP_PASSWORD"
    npm run test:e2e

The Playwright config starts Spring Boot and Vite. The disposable MySQL instance must already be running and empty.

## Completion gate

**Satisfied on the final Phase 7 branch head.** The dedicated full-stack workflow and paired frontend CI passed, the API lifecycle and role-isolation checks succeeded, all four role workspaces passed the configured axe/mobile checks, and the nine retained screenshots were visually reviewed. Phase 7 is complete on its feature branch. This does not merge the pull request, deploy the application, or claim that the application is free of all accessibility or security issues.
