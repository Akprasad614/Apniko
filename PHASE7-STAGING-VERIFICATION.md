# Phase 7 — end-to-end staging verification

**Status: Phase 7 implementation and its initial acceptance passed. Final acceptance is being rerun against the latest Phase 3–4 text-only development base before closeout.**
**Integration branch:** feature/phase-7-e2e-accessibility-staging
**Parent milestone:** Phase 6 role-based application delivery

## Verification record

The latest pre-integration acceptance ran on **24 November 2026**:

- Verified Phase 7 head: `8b71a4c3c7f03e4d267874616d3ab935c9605eee`
- Dedicated browser/accessibility/staging workflow: [run #45403357732](https://github.com/Akprasad614/Apniko/actions/runs/45403357732) — **passed**.
- Frontend CI: [run #45403357561](https://github.com/Akprasad614/Apniko/actions/runs/45403357561) — **passed**.
- Backend CI: [run #45403357874](https://github.com/Akprasad614/Apniko/actions/runs/45403357874) — **passed**.
- CodeQL: [run #45403357593](https://github.com/Akprasad614/Apniko/actions/runs/45403357593) — **passed**.
- Static security (Semgrep/Gitleaks): [run #45403357547](https://github.com/Akprasad614/Apniko/actions/runs/45403357547) — **passed**.
- CycloneDX SBOM: [run #45403357921](https://github.com/Akprasad614/Apniko/actions/runs/45403357921) — **passed**.
- OSV dependency scan: [run #45403358455](https://github.com/Akprasad614/Apniko/actions/runs/45403358455) — **passed**.
- Retained artifact: [apniko-phase7-e2e-29](https://github.com/Akprasad614/Apniko/actions/runs/45403357732/artifacts/14923598874), containing the Playwright report and nine desktop/mobile screenshots; retention ends **8 December 2026**.

The dedicated run completed every step, including the Phase 7 safety verifier, catalogue contract verifiers, Maven verification, Chromium installation, the full Playwright journey and artifact upload. Its browser run passed the customer checkout → shopkeeper fulfilment → delivery-partner pickup/delivery → customer order-history lifecycle, shopkeeper-to-admin HTTP 403 isolation, axe checks and mobile overflow checks.

**Integration qualification:** the above run started before the development base received the final Phase 3–4 text-only catalogue changes. Its screenshots therefore are not the final visual acceptance for the text-only catalogue requirement. A new pull-request run against the latest development base is required; only that run can close Phase 7.

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
