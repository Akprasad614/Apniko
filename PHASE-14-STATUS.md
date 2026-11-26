# Apniko — current phase status

| Phase | Responsibility | Status |
|---|---|---|
| 0 | Repository audit, architecture and database cleanup | Complete |
| 1 | Application foundation, authentication and navigation | Complete |
| 2 | Customer, Shopkeeper, Delivery Partner and Admin applications | Complete |
| 3 | Authentic Indian grocery product catalogue | Implemented; complete licensed retail dataset pending |
| 4 | Text-only catalogue and removal of product-image infrastructure | Complete |
| 5 | Admin catalogue management and bulk product import | Implemented; commercial source rights pending |
| 6 | Shopkeeper catalogue, inventory and shop management | Complete |
| 7 | Customer marketplace and shopping experience | Complete |
| 8 | Checkout and payment system | Implemented; Razorpay sandbox acceptance pending |
| 9 | Order management and delivery operations | Complete |
| 10 | GPS, customer location and nearby shops | Implemented; real-device/shop-pin verification pending |
| 11 | Apniko Khatha — customer credit and ledger | Implemented; staging regression and reconciliation pending |
| 12 | Notifications, analytics and operational insights | Complete |
| 13 | Integration, security and reliability | Repository verification passed; live staging acceptance pending |
| 14 | Staging deployment and testing | Versioned Flyway baseline/upgrade prepared; live staging acceptance pending |
| 15 | Production deployment and public launch | Blocked until Phase 14 sign-off |

## Phase 14 repository work

The staging handoff is integrated through PR #80. It includes the staging acceptance matrix, database migration-baseline procedure, owner deployment guide, Razorpay/Khatha test plan, retail catalogue licensing handoff, staging environment template, HTTPS smoke-test helper, readiness verifier and a dedicated GitHub Actions workflow.

The existing Compose, Caddy, production profile, health checks and guarded backup/restore scripts are retained.

The candidate database migration set contains a schema-only V1 snapshot from the owner's 26 November 2026 MySQL export and a V2 upgrade adding the five Khatha tables, shop coordinates, catalogue source metadata, supported payment enum values and safe legacy address-column nullability. Production migrations run separately with a DDL-only identity; the application runtime remains DML-only and Hibernate uses `validate`.

## Remaining release gates

Phase 14 is not complete until each of these has evidence from the real staging environment:

1. Inventory and review the actual MySQL schema; create and test a versioned migration baseline.
2. Deploy the application to a staging host with HTTPS.
3. Complete customer, shopkeeper, delivery-partner and admin acceptance tests.
4. Verify Razorpay sandbox payments, signatures, webhooks, retries and refunds.
5. Test Khatha credit limits, repayments, reversals, permissions and ledger reconciliation.
6. Verify shop GPS pins and browser location behaviour on supported Android and iOS devices.
7. Configure and test GitHub main-branch protection.
8. Configure external monitoring, alert delivery and restricted log retention.
9. Configure encrypted off-site backups and complete a restore drill into an isolated database.
10. Obtain commercial catalogue rights and verify retail product data before importing it.

**Phase 15 starts only after all Phase 14 gates pass and the release evidence is approved.**
