# Phase 13 — Integration, security and reliability

**Status: repository-side implementation complete; external production controls remain gated.**

Phase 13 is the cross-feature release audit after the customer, shopkeeper, delivery, payment, geolocation and Khatha work. It does not replace the feature-specific tests or claim that a live provider, production database or public deployment has been exercised.

## What this phase enforces

- Re-runs the Phase 4 text-only catalogue guard, Phase 5 admin catalogue guard, Phase 8 production/authentication guard and Phase 10 geolocation guard as one integration gate.
- Checks the application role boundaries for admin, shopkeeper, delivery partner and customer routes.
- Rejects global Spring CSRF disablement and requires the session cookie to remain HttpOnly.
- Checks that payment initiation locks the order, verifies the customer owns it, and uses the official Razorpay endpoint allowlist and HMAC signature verification.
- Checks Khatha ledger source uniqueness/idempotency, pessimistic account locking, India business timezone and shopkeeper repayment verification. The ledger repository exposes only append/read methods (not JpaRepository's inherited delete methods), while JPA lifecycle callbacks reject updates and deletes.
- Checks cancellation reversal accounting rather than deletion of financial history.
- Checks production schema validation, private health/error diagnostics and file-mounted database/JWT secrets.
- Syntax-checks the guarded backup/restore scripts and verifies that GitHub Actions references remain pinned to full commit SHAs with version comments.
- Runs as part of backend CI before the Maven verification suite. Existing frontend CI, Phase 7/8 browser acceptance, CodeQL, Semgrep, Gitleaks, OSV and SBOM workflows remain in place.

## Validation commands

```bash
bash scripts/verify-phase13-integration-reliability.sh
./mvnw -B -ntp verify
cd frontend && npm ci --no-audit --no-fund
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

The CI workflow executes the verifier and Maven suite; frontend CI executes the frontend checks. Phase 8's production-readiness workflow also builds the production containers and runs the browser acceptance suite against disposable CI infrastructure.

## Release boundaries — not completed by repository code

The following require owner/operator access and must not be simulated or marked complete by a passing CI run:

1. Generate and review a production SQL migration against a fresh backup of the actual target schema; apply it only during an approved maintenance window.
2. Provision hosting, DNS, TLS and production secrets in the selected hosting account.
3. Activate GitHub branch/ruleset protections and verify required checks on the intended integration branch.
4. Complete an encrypted off-site backup and restore drill against an isolated disposable database.
5. Run a Razorpay sandbox order, signed webhook, verification and refund using owner-controlled test credentials.
6. Verify each shop's saved location in person and run a small customer/shopkeeper pilot.
7. Configure external uptime/error monitoring, alert routing, log retention and incident ownership.
8. Obtain documented commercial reuse rights and provenance for any retail catalogue source before publishing imported products.

## Final repository integration note

Phase 10 location hardening was merged into the integration branch through PR
#77 at `3d863ff0b40f8f0118cbeb53cdfde884569c0e08`. Its backend, frontend,
Phase 7/8, CodeQL, OWASP ZAP, OSV, static-security and SBOM workflows all
passed on the pull-request head before merge.

Phase 5 now includes an owner-configurable commercial retail source gate and a
generic dry-run-first CSV importer. The Mendeley reference dataset remains
restricted to review candidates; a future retail source remains disabled until
the owner has reviewed its licence/agreement and explicitly configured the
exact source identity and commercial permissions. Imported retail rows remain
hidden in `NEEDS_REVIEW` pending administrator approval.

## Financial and catalogue safety

- No customer or shopkeeper can silently debit a bank/UPI account; Khatha repayments remain pending until shopkeeper verification.
- Ledger history is append-only at the application/JPA layer: repository deletes are not exposed and entity update/delete callbacks reject mutation; reversals are separate records. Direct privileged SQL remains an operator/database-control concern.
- Production Hibernate schema mode remains `validate`; no auto-DDL migration is performed in production.
- No product images, Open Food Facts data, unlicensed retailer catalogue copies, fabricated products or sample prices are introduced.

## Phase 13 exit criteria

Repository-side Phase 13 is complete when this verifier passes on the PR head and the required backend, frontend and security workflows pass. The external release boundaries above remain explicit prerequisites for Phase 14/15 and do not block starting the separate Phase 5 catalogue-management work, provided its source/licensing gate is respected.
