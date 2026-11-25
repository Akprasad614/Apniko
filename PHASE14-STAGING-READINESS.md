# Phase 14 — staging deployment and acceptance readiness

**Repository preparation:** integrated into `chore/phase-0-foundation-cleanup` through PR #80 (merge commit `85d04b931488667db2e05e01ba4110fc0d7445fd`).
**Environment status:** NOT DEPLOYED. No staging hostname, database, provider credentials, mobile device, monitoring account or backup target has been configured as part of the repository work.
**Release rule:** A green CI run is not staging acceptance. Record evidence for every owner-run gate below before declaring Phase 14 accepted.

## Scope boundary

This phase prepares repository automation, safety checks and an operator procedure. The project owner controls the staging database, host, DNS, secrets, Razorpay sandbox account, mobile devices, backup destination and production deployment. Never point an unknown or newly provisioned database at the application before the schema review and migration baseline have been approved.

## Database migration gate (mandatory)

1. Obtain a fresh, verified backup of the owner-selected known-good schema. Preserve a second encrypted copy outside the database host.
2. Identify the authoritative schema source: the actual application database and the exact code revision that created it. Do not infer a baseline from entity annotations alone.
3. Restore the backup into an isolated disposable database. Record server version, database name, charset/collation, tables, views, triggers, routines, events, indexes, foreign keys and migration history, if any.
4. Compare the restored schema with the complete JPA entity mapping and application SQL. Resolve every mismatch before proceeding.
5. Prepare a Flyway baseline and migration set from the reviewed schema snapshot. The first migration must represent the reviewed actual schema; subsequent migrations must be explicit, ordered and repeatable in a clean disposable database.
6. Test both paths: (a) empty disposable database to baseline plus all migrations; (b) restored schema copy to the baseline marker and subsequent migrations as designed. Compare the resulting schemas and run the backend suite.
7. Obtain approval for the SQL and backup/restore evidence. Only then may the staging migration identity apply the migration. The runtime identity remains DML-only.
8. Production requires separate approval and a verified backup. Do not set Hibernate to `update`; production remains in `validate` mode.

No migration SQL is included in this repository change because the canonical application schema has not been inspected. Do not generate or apply a guessed baseline.

## Staging infrastructure

- Linux Docker host with Docker Engine and the Compose plugin; DNS records pointed at the host; inbound TCP 80/443 and UDP 443 as appropriate.
- Separate managed MySQL database and credentials for staging, with TLS hostname verification enabled.
- Separate DDL migration identity and DML-only runtime identity.
- Staging secrets created directly on the host or in a secret manager. Do not put them in GitHub Actions variables, source files, frontend `VITE_*` values, Docker build arguments or chat.
- Immutable application image/revision, Caddy automatic TLS, readiness health checks and no public MySQL/backend ports.
- Staging isolated from production customer records and credentials. Use synthetic accounts and data.

## Automated preparation checks

Run before deployment:

```bash
bash scripts/verify-phase14-readiness.sh
bash scripts/verify-phase8.sh
./mvnw -B -ntp verify
cd frontend
npm ci --no-audit --no-fund
npm run lint
npm run typecheck
npm run test:ci
npm run build
```

The PR workflow runs the repository handoff checks and validates Compose configuration with disposable values. Existing backend, frontend, security and full-browser checks verify repository behaviour; they do not prove live staging acceptance.

## Staging deployment

1. Complete and approve the schema/migration gate.
2. Provision the staging host, DNS, database, TLS ingress and least-privilege database identities.
3. Create `secrets/db_password.txt` and `secrets/jwt_secret.txt` on the host with restrictive permissions (see `OPERATIONS-RUNBOOK.md`).
4. Copy `.env.production.example` to a private `.env.staging` file. Set the staging hostname, TLS-verified database URL using `sslMode=VERIFY_IDENTITY`, non-root runtime user and immutable image tag. Never commit the populated file.
5. Inspect Compose expansion and build/deploy from a reviewed commit:

```bash
docker compose --env-file .env.staging -f compose.yaml config --quiet
docker compose --env-file .env.staging -f compose.yaml build --pull
docker compose --env-file .env.staging -f compose.yaml up -d
docker compose --env-file .env.staging -f compose.yaml ps
docker compose --env-file .env.staging -f compose.yaml logs --tail=200 backend frontend caddy
```

6. Confirm HTTPS certificate validity, frontend health, backend readiness, no public actuator/database ports and no secret leakage in logs.
7. Apply only the reviewed staging migration using the migration identity, then run the application with Hibernate validation. Retain a pre-migration backup and the previous immutable image.

## Staging acceptance evidence

Every item below must have a result, timestamp and evidence reference.

### Core application and roles

- [ ] Customer registration, login, logout, session restoration, CSRF and account access.
- [ ] Shop discovery, category/product search, shop-scoped catalogue, cart quantities and totals.
- [ ] Checkout creates the expected order; duplicate/retry behaviour does not create duplicate charges or orders.
- [ ] Shopkeeper accepts, prepares and marks orders ready; inventory and ownership boundaries hold.
- [ ] Delivery assignment, acceptance, pickup and delivery; customer sees correct status/history.
- [ ] Admin access, catalogue moderation and audit trail.
- [ ] Mobile layouts, keyboard navigation, loading/error/empty states and accessibility.

### Payments — Razorpay sandbox only

- [ ] Create payment order with correct INR minor-unit amount and local order mapping.
- [ ] Successful sandbox payment is verified server-side; a browser callback alone never marks an order paid.
- [ ] Invalid signature, mismatched amount/currency/order, replayed webhook and out-of-order webhook are rejected or handled idempotently.
- [ ] Duplicate webhook does not settle the payment/order twice.
- [ ] Failure, cancellation, timeout and retry paths leave order/payment states consistent.
- [ ] Full/partial refund where supported; duplicate refund and provider failure are handled safely.
- [ ] No live Razorpay keys or real-money transactions during staging.

Record provider dashboard evidence with identifiers redacted. Never store secrets or full payment payloads in logs.

### Apniko Khatha

- [ ] Only eligible customer/account can use credit; limit and outstanding-balance checks are atomic under concurrent requests.
- [ ] Ledger entries are append-only and amount semantics are consistent; no direct balance mutation.
- [ ] Repayment references and provider/payment linkage are unique and idempotent.
- [ ] Duplicate repayment/webhook does not credit twice.
- [ ] Reversal creates a compensating entry; the original ledger entry is never edited/deleted.
- [ ] Actor permissions and audit history are verified; inactive/blocked accounts cannot transact.
- [ ] Over-limit, negative, zero, overflow, cross-customer and concurrent requests fail safely.
- [ ] Ledger-derived balances reconcile with account summaries and payment records.

### GPS and mobile devices

- [ ] Test supported Android and iOS browsers with location permission granted, denied and revoked.
- [ ] Confirm coordinate order, map-pin placement, nearby-shop distance/radius and permission fallback.
- [ ] Manual address entry remains available; checkout is not blocked solely because GPS permission is denied.
- [ ] Verify shop coordinates on site with shop owners; do not rely on approximate seeded/test coordinates.
- [ ] Confirm HTTPS is used for browser geolocation.

### Operations

- [ ] External HTTPS uptime and synthetic health monitor.
- [ ] Alerts for 5xx/error rate, container restarts, readiness failure, database pool/exhaustion, disk capacity, TLS expiry and payment webhook failures.
- [ ] Restricted centralized logs; no passwords, JWTs, secrets, OTPs or payment credentials.
- [ ] Encrypted off-site backups with documented retention; checksum verification and successful restore into an isolated staging database.
- [ ] Measured RPO/RTO, restore-drill evidence and access review recorded.
- [ ] Rollback to the previous immutable application image rehearsed; schema rollback is not assumed.
- [ ] Incident contact, credential rotation, data-breach escalation, payment reconciliation and customer communication responsibilities assigned.

## Sign-off record

Complete the table with evidence references and accountable operator details.

| Gate | Evidence / reference | Owner/date | Result |
|---|---|---|---|
| Schema inventory and reviewed migration baseline | | | Pending |
| Staging HTTPS deployment | | | Pending |
| Application/role acceptance | | | Pending |
| Razorpay sandbox, webhook and refund acceptance | | | Pending |
| Khatha regression and reconciliation | | | Pending |
| Android/iOS GPS verification | | | Pending |
| GitHub branch protection | | | Pending |
| Monitoring and alert delivery | | | Pending |
| Encrypted off-site backup and restore drill | | | Pending |
| Catalogue rights and import approval | | | Pending |
| Rollback rehearsal and release record | | | Pending |

**Phase 14 exit criterion:** every gate above has reproducible evidence and owner sign-off. Repository preparation alone does not satisfy Phase 14. Phase 15 remains blocked until these checks pass.
