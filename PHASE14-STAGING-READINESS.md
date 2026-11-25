# Phase 14 — staging deployment and acceptance readiness

**Repository preparation:** this document and its companion scripts are prepared on `chore/phase14-deployment-readiness`.
**Environment status:** NOT DEPLOYED. No staging hostname, database, provider credentials, phone, monitoring account or backup target was available to this repository task.
**Release rule:** A green CI run is not staging acceptance. Record evidence for every owner-run gate below before declaring Phase 14 accepted.

## Scope boundary

This phase prepares repository automation, safety checks and an operator procedure. The owner retains control of the staging database, host, DNS, secrets, Razorpay sandbox account, mobile devices, backup destination and production deployment. Never point a new/unknown database at the application before the schema review and migration baseline have been signed off.

## Database migration gate (mandatory)

1. Obtain a fresh, verified backup of the owner-selected known-good schema. Preserve a second encrypted copy outside the database host.
2. Identify the authoritative schema source: the actual application database plus the exact code revision that created it. Do not infer a baseline from entity annotations alone.
3. Restore the backup into a disposable isolated database. Record server version, database name, charset/collation, tables, views, triggers, routines, events, indexes, foreign keys and migration history if any.
4. Compare the restored schema with the complete JPA entity mapping and application SQL. Resolve every mismatch with the domain owner.
5. Generate a Flyway baseline/migration set from the reviewed schema snapshot. The first migration must represent the reviewed actual schema; subsequent migrations must be additive/explicit, ordered, repeatable in a clean disposable database and reviewed as SQL.
6. Test both paths: (a) empty disposable database → baseline + all migrations; (b) restored schema copy → baseline marker / migrations as designed. Compare both resulting schemas and run the backend suite.
7. Have the owner approve the SQL and backup/restore evidence. Only then may the staging migration identity run the migration. The runtime identity remains DML-only.
8. Production requires a separate approval and verified backup. Do not set Hibernate to `update`; production remains `validate`.

No migration SQL is supplied in this change because the actual canonical schema has not been inspected. An invented baseline is more dangerous than a deliberately empty migration directory.

## Staging infrastructure

- Linux Docker host with Docker Engine + Compose plugin; DNS A/AAAA records pointed at the host; TCP 80/443 and UDP 443 as appropriate.
- Separate managed MySQL database and credentials for staging; TLS hostname verification enabled; separate DDL migration identity and DML-only runtime identity.
- Staging secrets created directly on host/secret manager; never GitHub Actions variables, source files, frontend `VITE_*` values, build args or chat.
- Exact immutable image/revision, Caddy automatic TLS, readiness health checks, no public MySQL/backend ports.
- Staging must be isolated from production customer records and credentials. Use synthetic accounts/data.

## Automated preparation checks

Run before deployment:

```bash
bash scripts/verify-phase14-readiness.sh
bash scripts/verify-phase8.sh
./mvnw -B -ntp verify
cd frontend && npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm run test:ci && npm run build
```

The PR workflow also runs existing backend, frontend, security and full-browser acceptance workflows. Their GitHub results are repository checks, not evidence of live staging acceptance.

## Deploy staging

1. Complete and approve the schema/migration gate.
2. Provision staging host, DNS, database, TLS ingress and least-privilege identities.
3. Create `secrets/db_password.txt` and `secrets/jwt_secret.txt` on the host with restrictive permissions (see `OPERATIONS-RUNBOOK.md`).
4. Copy `.env.production.example` to a private staging env file and set actual staging hostname, managed DB URL with `sslMode=VERIFY_IDENTITY`, non-root runtime user, and immutable tag. Never commit it.
5. Inspect Compose expansion and build/deploy from a reviewed commit:

```bash
docker compose --env-file .env.staging -f compose.yaml config --quiet
docker compose --env-file .env.staging -f compose.yaml build --pull
docker compose --env-file .env.staging -f compose.yaml up -d
docker compose --env-file .env.staging -f compose.yaml ps
docker compose --env-file .env.staging -f compose.yaml logs --tail=200 backend frontend caddy
```

6. Confirm HTTPS certificate validity, frontend health, backend readiness, no public actuator/database ports and no secret leakage in logs.
7. Apply only the reviewed staging migration using the migration identity, then restart/rollout application with Hibernate validate. Retain pre-migration backup and immutable previous image.

## Staging acceptance evidence (all required)

### Core application / roles
- [ ] Customer registration/login/logout/session restore/CSRF and account access.
- [ ] Shop discovery, category/product search, shop-scoped catalog, cart quantity and totals.
- [ ] Checkout creates one expected order; duplicate/retry behaviour does not create duplicate charges/orders.
- [ ] Shopkeeper accepts/prepares/marks ready; inventory and order ownership boundaries hold.
- [ ] Delivery assignment/accept/pickup/deliver; customer sees correct status/history.
- [ ] Admin access, catalogue moderation and audit trail.
- [ ] Mobile layouts, keyboard navigation, loading/error/empty states and accessibility.

### Payments (Razorpay sandbox only)
- [ ] Create order uses INR minor units and correct local order linkage.
- [ ] Successful sandbox payment verifies server-side; browser success callback alone never marks paid.
- [ ] Invalid signature, mismatched amount/currency/order, replayed webhook and out-of-order webhook are rejected or safely idempotent.
- [ ] Duplicate webhook does not double-settle the payment/order.
- [ ] Failure, cancellation, timeout and retry paths leave order/payment states consistent.
- [ ] Full/partial refund as supported; duplicate refund and provider failure handled safely.
- [ ] No live Razorpay keys or real-money transfer during staging.
- Record provider dashboard evidence with IDs redacted; never store secrets or full payment payloads in logs.

### Apniko Khatha (financial safeguards)
- [ ] Only eligible customer/account can incur credit; limit and outstanding-balance checks are atomic under concurrent requests.
- [ ] Ledger entries are append-only and signed/amount semantics are consistent; no direct balance mutation.
- [ ] Repayment references and provider/payment linkage are unique/idempotent.
- [ ] Duplicate repayment/webhook does not credit twice.
- [ ] Reversal creates a compensating entry; it never edits/deletes the original ledger entry.
- [ ] Admin/actor permissions and audit history verified; inactive/blocked accounts cannot transact.
- [ ] Over-limit, negative, zero, overflow, cross-customer and concurrent requests fail safely.
- [ ] Reconcile ledger-derived balances against displayed account summaries and payments.

### GPS / mobile devices
- [ ] Test actual Android and iOS browsers supported by the release, with permission granted, denied and revoked.
- [ ] Confirm location accuracy/lat-long order, map pin placement, nearby-shop distance/radius and permission fallback.
- [ ] Check manual address entry remains possible; never block checkout solely on denied GPS.
- [ ] Verify shop coordinates on site with shop owners; do not rely on approximate seeded/test coordinates.
- [ ] Confirm HTTPS is used; browser geolocation is not expected on insecure origins.

### Operations
- [ ] External HTTPS uptime and synthetic health monitor from outside the host.
- [ ] Alerts for 5xx/error rate, container restarts, readiness failure, DB pool/exhaustion, disk, TLS expiry and payment webhook failures.
- [ ] Restricted centralized logs; no passwords, JWTs, secrets, OTPs or payment credentials.
- [ ] Encrypted off-site backups with documented retention; successful checksum verification and restore into isolated staging DB.
- [ ] Record measured RPO/RTO; backup/restore drill evidence and access review.
- [ ] Test rollback to previous immutable image; schema rollback is not assumed. Confirm forward compatibility.
- [ ] Incident contact, credential rotation, data breach escalation, payment reconciliation and customer communication owner assigned.

## Sign-off record

Complete this table in the PR/release record with dates, links and accountable operator.

| Gate | Evidence / reference | Owner/date | Result |
|---|---|---|---|
| Schema inventory and reviewed migration baseline | | | Pending |
| Staging HTTPS deploy | | | Pending |
| Staging application/role acceptance | | | Pending |
| Razorpay sandbox & webhook/refund acceptance | | | Pending |
| Khatha regression/reconciliation | | | Pending |
| Android/iOS GPS verification | | | Pending |
| GitHub branch protection/rulesets | | | Pending |
| Monitoring and alert delivery | | | Pending |
| Encrypted off-site backup + restore drill | | | Pending |
| Catalogue rights/import approval | | | Pending |
| Rollback rehearsal and release record | | | Pending |

**Phase 14 exit criterion:** every gate above has reproducible evidence and owner sign-off. Repository preparation alone does not satisfy Phase 14. Phase 15 must remain blocked until then.
