# Apniko — owner-operated deployment guide

This is the handoff for the person who provisions infrastructure and performs deployment. It intentionally does not automate cloud account creation, database changes, DNS, payments or production release approval.

## A. Before touching a database

- Choose the authoritative existing schema or intentionally provision a fresh database only after the full reviewed schema migration baseline is available.
- Back up the known-good database and verify the dump checksum.
- Restore a copy to a disposable database and inspect it. Do not run SQL generated from a guessed entity schema.
- Review the full migration SQL with the project owner. Establish the first Flyway baseline and test clean-install and upgrade paths.
- Keep Hibernate production mode at `validate`. Do not set `ddl-auto=update`.

The current repository does not contain the reviewed complete baseline. Stop here for any new production database until it has been prepared and reviewed.

## B. Host and DNS

1. Provision a supported Linux VPS/VM, Docker Engine and Docker Compose plugin.
2. Point the chosen DNS hostname to the host and allow inbound TCP 80/443 and UDP 443.
3. Provision managed MySQL 8.x with TLS and certificate/hostname verification. Use separate migration and runtime identities. Runtime should have only required DML privileges.
4. Set up a secret manager or private host-only files. Do not send secrets in chat or commit them.
5. Configure an external uptime/metrics/logging provider and alert destination.

## C. Host secrets and environment

From a private deployment checkout:

```bash
mkdir -p secrets
chmod 700 secrets
umask 077
openssl rand -base64 48 > secrets/jwt_secret.txt
# Create secrets/db_password.txt securely with the runtime DB password.
chmod 600 secrets/db_password.txt secrets/jwt_secret.txt
cp .env.production.example .env.production
chmod 600 .env.production
```

Edit the private `.env.production`: set the public hostname, operations email, TLS-verified JDBC URL, runtime username and image tag to an immutable Git commit/release tag. Keep catalogue source permission flags false until an actual source owner has granted and documented commercial-use rights.

## D. Verify/build/deploy

```bash
git fetch origin
git checkout <reviewed-release-tag-or-commit>
git status --short
bash scripts/verify-phase14-readiness.sh
docker compose --env-file .env.production config --quiet
docker compose --env-file .env.production build --pull
docker compose --env-file .env.production up -d
docker compose --env-file .env.production ps
docker compose --env-file .env.production logs --tail=150 backend frontend caddy
```

Do not deploy from a dirty worktree. Do not use `latest`. The app should not start successfully if DB/JWT secrets are missing. Confirm backend readiness from inside the backend container:

```bash
docker compose --env-file .env.production exec backend wget -qO- http://127.0.0.1:8080/actuator/health/readiness
```

Then verify public HTTPS, browser login/CSRF, role flows and no public database/actuator access. Perform Phase 14 staging acceptance before any production deployment.

## E. GitHub release controls

Configure on GitHub Settings → Rules → Rulesets (or Branches → branch protection rules, depending on repository UI):
- Target branch: `main`.
- Require pull request before merging; require at least one approval if another reviewer exists.
- Dismiss stale approvals when new commits are pushed; require conversation resolution.
- Require the exact CI/security status checks produced by this repository.
- Require branches up to date if workflow duration permits.
- Block force pushes and branch deletion.
- Restrict bypass to a narrowly controlled administrator emergency path; document any bypass.

Verify by attempting a test PR with a failing required check and confirming it cannot merge. Then verify the normal approved/passing PR path. The repository automation connection may be unable to administer rulesets; this remains an owner UI task.

## F. Backups, restore and rollback

Use `scripts/backup-production-db.sh` with TLS verification. Copy the dump to a separate encrypted account, verify checksum after upload, and retain according to policy. Run `scripts/restore-production-db.sh` only against an explicitly selected isolated restore target after setting both confirmations; it overwrites tables.

Rehearse the restore into isolated staging and measure RPO/RTO. Keep at least the previous immutable application image. Roll back the image only after checking schema compatibility; never assume an old image can use a newer schema. Prefer reviewed forward-fix migrations.

## G. Production promotion (Phase 15)

Only after Phase 14 is signed off:
1. Review release commit, image, migrations, SBOM/security scans, staging evidence and approver.
2. Verify production backup and point-in-time recovery posture.
3. Confirm secrets and Razorpay live credentials are provisioned directly in the host/secret store.
4. Apply the approved production migration with the temporary migration identity.
5. Deploy immutable image and run smoke checks.
6. Observe monitoring, webhook delivery, payment reconciliation and order/Khatha ledgers.
7. Keep rollback image, incident contacts and freeze/rollback decision owner available.

Never test with real customer transactions until an explicit controlled launch decision is made.
