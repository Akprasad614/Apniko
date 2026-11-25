# Apniko — Phases 0–13 completion and Phase 14 handoff

**Repository integration branch:** `chore/phase-0-foundation-cleanup`  
**Latest integrated Phase 10 merge:** `3d863ff0b40f8f0118cbeb53cdfde884569c0e08`  
**Main branch:** not modified by this work.

This handoff separates repository implementation from work that must be performed by the project owner in the actual staging/production environment.

## Phase status

| Phase | Scope | Repository status |
|---:|---|---|
| 0 | Repository audit, architecture and database cleanup | Complete |
| 1 | Application foundation, authentication and navigation | Complete |
| 2 | Customer, Shopkeeper, Delivery Partner and Admin applications | Complete |
| 3 | Authentic Indian grocery product catalogue | Complete |
| 4 | Text-only catalogue; no product image infrastructure or Open Food Facts | Complete |
| 5 | Admin catalogue management and bulk import | Complete* |
| 6 | Shopkeeper catalogue, inventory and shop management | Complete |
| 7 | Customer marketplace and shopping experience | Complete |
| 8 | Checkout and payment system | Complete* |
| 9 | Order management and delivery operations | Complete |
| 10 | GPS, customer location and nearby shops | Complete* |
| 11 | Apniko Khatha — customer credit and ledger | Complete |
| 12 | Notifications, analytics and operational insights | Complete |
| 13 | Integration, security and reliability | Complete* |

* A repository implementation milestone is complete; the owner-run verification gates below are deliberately not represented as having happened.

## What was completed for this handoff

- Phase 10 PR #77 was merged into the integration branch after all eight observed PR workflows passed.
- Phase 5 gained a configurable owner-approved commercial source gate and a general retail CSV importer with dry-run default, source-record uniqueness, GTIN check-digit validation, retail quantity/pack checks, source provenance, bounded batches and admin-only submission.
- New generic retail imports are always held in `NEEDS_REVIEW`; they do not become customer-visible until an administrator approves them.
- Existing Mendeley intake remains reference-only. It does not provide retail GTIN/pack identity and is not treated as a retail SKU feed.
- Phase 4's text-only rule remains intact. No product photos, image URLs, Open Food Facts data, copied retailer feed or fabricated SKU data are introduced.
- Phase 8 production configuration continues to use Hibernate `validate`, externally supplied secrets, private service ports, health probes and guarded backup/restore scripts.
- Phase 13 continues to run combined checks for Phase 4, 5, 8 and 10 along with payment, Khatha, access-control, supply-chain and recovery invariants.

## Owner actions during Phase 14/15

These actions must be carried out by the owner with access to the actual environment. They are not safe to simulate in a repository PR:

1. **Database:** take a backup, inspect the real schema, prepare and review versioned migrations against that schema, then apply them to the staging database. Do not infer a production baseline from JPA entities or run a destructive reset.
2. **Staging deployment:** configure the chosen host, DNS, TLS, container image tags, environment variables and secret files. Keep database and application services private behind the HTTPS ingress.
3. **Payment sandbox:** configure Razorpay test keys and webhook secret directly in the provider dashboard; verify a test order, signed webhook, payment verification and refund before considering live credentials.
4. **Location data:** have an administrator verify each real shop entrance pin. Test customer location permission and nearby search on actual HTTPS-enabled phones.
5. **Retail source:** obtain a lawful export or written commercial-use permission. Configure the exact source identity and rights flags on the backend host only after legal/contract review; run a dry-run CSV import first and moderate every row.
6. **Operations:** configure GitHub branch protection/rulesets, external monitoring and alerting, encrypted off-site backup retention, and a successful restore drill against an isolated database.

Never commit real database passwords, JWT secrets, payment keys, webhook secrets, admin tokens, private agreement files or populated `.env.production` files.

## Start Phase 14 when ready

Phase 14 is staging deployment and end-to-end verification. Start only after this implementation PR is merged. The first staging run should use a fresh backup and a staging-only database; no production data should be used for early verification.
