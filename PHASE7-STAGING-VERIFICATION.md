# Phase 7 — end-to-end staging verification

**Status: Original Phase 7 branch acceptance passed; consolidated integration is being revalidated.** The historical evidence below records the pre-integration branch. The current integrated tree must pass Phase 7 again before being marked complete.
**Integration branch:** feature/phase-7-e2e-accessibility-staging
**Parent milestone:** Phase 6 role-based application delivery

## Verification record

Final acceptance was run on **23 November 2026** on the current Phase 7 branch head:

- Verified final branch head: `768fad50bbd7ce56204e56c8ced1d6161d4cf407`
- Dedicated GitHub Actions run: [#45235967198 — browser, accessibility and staging verification](https://github.com/Akprasad614/Apniko/actions/runs/45235967198) — **passed**
- Frontend CI: [#45235967217](https://github.com/Akprasad614/Apniko/actions/runs/45235967217) — **passed**
- Retained evidence artifact: [apniko-phase7-e2e-22](https://github.com/Akprasad614/Apniko/actions/runs/45235967198/artifacts/14849856177), retained until **7 December 2026**.

The successful dedicated workflow confirms:

- Backend Maven verification and tests passed against disposable MySQL 8.4.
- The real API-backed customer checkout → shopkeeper fulfilment → delivery-partner pickup/delivery → customer order-history journey passed.
- The shopkeeper-to-admin access-isolation request returned HTTP 403 as expected.
- axe-core WCAG 2.0/2.1 A/AA checks passed for customer, shopkeeper, delivery and administrator workspaces; the test fails on any critical or serious violation.
- 390 × 844 mobile overflow checks passed for all four roles.
- Frontend lint, typecheck, unit tests and production build passed in the paired frontend CI run.

The final artifact was downloaded and visually reviewed during closeout on **24 November 2026**. It contains nine screenshots: desktop and mobile views for customer, shopkeeper, delivery and administrator, plus the delivered customer order-history view. The layouts are present at both target sizes, with no obvious clipped or horizontally overflowing workspace in the retained evidence. The artifact also contains the Playwright HTML report and test attachments.

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
