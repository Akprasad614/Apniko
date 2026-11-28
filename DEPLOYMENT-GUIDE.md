# Apniko — owner-operated deployment guide

This is the handoff for the person who provisions infrastructure and performs deployment. It intentionally does not automate cloud account creation, database changes, DNS, payments or production release approval.

## A. Before touching a database

- The repository now includes a schema-only V1 snapshot of the inspected 18-table legacy `localcart` schema and a V2 forward migration to the current Apniko entity mappings. Review `DATABASE-MIGRATION-BASELINE.md`.
- Back up the known-good database, copy it to an encrypted off-host location and verify the dump checksum.
- Restore a copy to a disposable database and test both the empty-database and legacy-upgrade paths before staging.
- For a new empty database, run Flyway migrate (V1 then V2). For an existing database, baseline at version 1 only after verifying that its schema exactly matches V1, then run migrate.
- Run Flyway with a separate DDL-only migration identity. The runtime identity remains DML-only.
- Keep Hibernate in `validate` mode. Do not set `ddl-auto=update`. Production app startup does not run Flyway.


## A.1 No-cost staging route (Render + Cloudflare Pages + TiDB)

Use this route for a free staging/demo deployment without buying a VPS or custom domain. It is **not production hosting**. Render explicitly says its Free instances are for testing and hobby projects, not production applications.

### Why the frontend uses Cloudflare Pages

Do not host both browser frontend and cookie-authenticated API on separate `*.onrender.com` hostnames. Render's subdomain is a public suffix, so sibling services cannot share the browser's same-site cookies. Instead:

- **Backend:** one Render Free Docker web service in Singapore, built from the repository-root `Dockerfile`.
- **Frontend:** Cloudflare Pages, built from the `frontend` directory and served from its free `*.pages.dev` hostname.
- **API routing:** the Pages Function at `frontend/functions/api/[[path]].ts` proxies browser `/api/*` requests to the Render backend. The browser keeps one origin for React, API requests, HttpOnly auth cookies and CSRF cookies. Do not set `VITE_API_BASE_URL`; it must remain empty so the client calls relative `/api` paths.
- **Database:** the existing TiDB Cloud Starter instance and `apniko_staging` database. Only the `apniko_runtime` DML account is used by the backend. The `apniko_migrator` account is reserved for one-off Flyway migrations.

Cloudflare Pages Functions preserve individual `Set-Cookie` headers and disable caching for proxied API responses. Set the Pages runtime variable `APNIKO_API_ORIGIN` to the Render backend's HTTPS origin, with no trailing path, for example `https://apniko-api-staging.onrender.com`.

### Render backend service

Create **New → Web Service**, connect `Akprasad614/Apniko`, and configure:

| Setting | Value |
|---|---|
| Name | `apniko-api-staging` (or an available unique name) |
| Branch | `chore/phase-0-foundation-cleanup` until a reviewed release branch is selected |
| Region | Singapore |
| Runtime | Docker |
| Dockerfile path | `./Dockerfile` |
| Docker context | `.` |
| Instance type | Free |
| Health check path | `/actuator/health/readiness` |

Set these runtime environment variables in Render (never in Git):

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `10000` |
| `DB_URL` | The TiDB TLS JDBC URL for `apniko_staging` |
| `DB_USERNAME` | The prefixed `apniko_runtime` login |
| `DB_PASSWORD` | Contents of the local `secrets/tidb_runtime_password.txt` file |
| `JWT_SECRET` | A newly generated random secret (at least 32 bytes) |
| `DB_POOL_SIZE` | `5` |
| `DB_MIN_IDLE` | `1` |

The Docker entrypoint supports both mounted secret files (for Compose) and encrypted runtime environment variables (for Render). The container never receives the migration password. Keep Flyway disabled in the application runtime and do not add the migrator account to this service.

### Cloudflare Pages frontend

Create a Pages project from the same repository and set:

| Setting | Value |
|---|---|
| Production branch | `chore/phase-0-foundation-cleanup` |
| Root directory | `frontend` |
| Framework preset | Vite |
| Build command | `npm ci --no-audit --no-fund && npm run build` |
| Build output directory | `dist` |
| Build variable | `VITE_AUTH_COOKIE_MODE=true` |
| Runtime variable for Pages Functions | `APNIKO_API_ORIGIN=https://<your-render-service>.onrender.com` |

Leave `VITE_API_BASE_URL` unset/empty. Do not put database credentials, JWT secrets, Razorpay secrets, or any private value in a `VITE_*` variable. The API proxy runs at the Pages origin so the browser does not make cross-origin credentialed requests.

Set Pages Functions to **fail closed** if the free daily request quota is exhausted. Otherwise an API request could fall through to static content and return HTML where JSON is expected.

### Free-tier constraints

- Render Free web services have 512 MB RAM, can spin down after 15 minutes without inbound traffic, and typically take about a minute to wake. Render can restart them, and their local filesystem is ephemeral.
- Render grants 750 free instance hours per workspace per calendar month. Keep only the one backend Free service running.
- Cloudflare Pages static asset requests are free; Pages Functions use the Workers Free allowance of 100,000 requests per day.
- TiDB Starter has usage quotas; monitor storage and Request Units in the TiDB dashboard.
- No custom domain is required for the staging demo. Render and Cloudflare provide HTTPS service hostnames.
- Use synthetic staging accounts and data only. Do not process real customer orders or live payments on these free services. Use Razorpay sandbox credentials only.

This no-cost arrangement is a staging/portfolio demonstration. It does not satisfy Apniko's production reliability, off-site backup, uptime, monitoring, payment or commercial launch requirements. Review the provider's current terms and quotas before deployment; free plans can change.

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

Before starting Compose, apply the reviewed migrations with the separate DDL-only identity as described in `DATABASE-MIGRATION-BASELINE.md`. The `prod` profile disables Flyway in the application container; do not grant schema-change privileges to the runtime user. Do not deploy from a dirty worktree or use `latest`. The app should not start successfully if DB/JWT secrets are missing. Confirm backend readiness from inside the backend container:

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
