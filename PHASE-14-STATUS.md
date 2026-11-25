# Apniko — current phase status

| Phase | Responsibility | Status |
|---|---|---|
| 0 | Repository audit, architecture and database cleanup | Complete |
| 1 | Application foundation, authentication and navigation | Complete |
| 2 | Customer, Shopkeeper, Delivery Partner and Admin applications | Complete |
| 3 | Authentic Indian grocery product catalogue | Implemented with approved-source limitations; retail dataset still incomplete |
| 4 | Text-only catalogue / remove image infrastructure | Complete |
| 5 | Admin catalogue management and bulk import | Implemented; commercial retail source rights still pending |
| 6 | Shopkeeper catalogue, inventory and shop management | Complete |
| 7 | Customer marketplace and shopping experience | Complete |
| 8 | Checkout and payment system | Implemented; real Razorpay sandbox acceptance pending |
| 9 | Order management and delivery operations | Complete |
| 10 | GPS, customer location and nearby shops | Implemented; real-device/site-pin verification pending |
| 11 | Apniko Khatha — customer credit and ledger | Implemented; staging financial regression and reconciliation pending |
| 12 | Notifications, analytics and operational insights | Complete |
| 13 | Integration, security and reliability | Repository verification passed; staging acceptance pending |
| 14 | Staging deployment and testing | **Repository preparation in review; staging not deployed / not accepted** |
| 15 | Production deployment and public launch | **Blocked — do not start** |
| 16 | Future AI features and intelligent automation | Planned |

## Phase 14 current result

Repository-side handoff now includes staging acceptance gates, database migration-baseline procedure, owner deployment guide, payment/Khatha test plan, a staging environment template, a readiness verifier and a PR workflow. Existing Compose/Caddy/health/backup/restore automation is retained.

Outstanding owner-run gates are listed in `PHASE14-STAGING-READINESS.md`: actual schema inventory and reviewed migration baseline, real staging HTTPS deploy, application and role acceptance, Razorpay sandbox/webhook/refund tests, Khatha financial regression/reconciliation, Android/iOS GPS verification, main-branch protections, external monitoring/alert delivery, encrypted off-site backup + successful restore drill, and retail catalogue commercial rights and verified products.

**Phase 14 is not complete until all those checks are performed against staging with evidence. Phase 15 starts only after Phase 14 has passed and has owner sign-off.**
