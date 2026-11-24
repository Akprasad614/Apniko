# Apniko production operations runbook

This runbook describes the container deployment included in Phase 8. It uses Caddy for automated HTTPS, Nginx for the SPA and same-origin API proxy, Spring Boot for the API, and a separately managed MySQL database.

## 1. Preconditions

- A Linux host with Docker Engine and the Docker Compose plugin.
- A DNS hostname pointing to the host; inbound TCP 80/443 and UDP 443 are allowed.
- A managed MySQL 8.x database with TLS enabled, a valid CA chain, automated backups and a runtime database user with only application DML privileges.
- The exact production schema has been installed and validated. The current project still needs a reviewed baseline/migration set before the first production database is created; production startup uses Hibernate `validate`, not automatic schema mutation.
- A separate staging environment must pass the full Phase 7 browser journey before a release is promoted.

## 2. Create the runtime secrets

On the deployment host, from the repository release directory:

```bash
mkdir -p secrets
chmod 700 secrets
umask 077
openssl rand -base64 48 > secrets/jwt_secret.txt
# Put the managed database user's password into secrets/db_password.txt
chmod 600 secrets/db_password.txt secrets/jwt_secret.txt
cp .env.production.example .env.production
chmod 600 .env.production
```

Edit `.env.production` with the actual hostname, TLS email, managed DB JDBC URL and least-privilege username. The DB JDBC URL must use `sslMode=VERIFY_IDENTITY`. Set `APNIKO_IMAGE_TAG` to an immutable release/commit identifier, not `latest`.

Do not put a password, JWT secret, API key or payment-provider secret in a `VITE_*` variable, Docker build argument, GitHub repository variable, source file or workflow log. The backend reads DB and JWT values from mounted Docker secret files at container startup.

## 3. Build and start

```bash
docker compose --env-file .env.production config --quiet
docker compose --env-file .env.production build
docker compose --env-file .env.production up -d
docker compose --env-file .env.production ps
docker compose --env-file .env.production logs --tail=150 backend frontend caddy
```

Caddy obtains and renews the TLS certificate automatically. Keep its `caddy_data` volume backed up; it stores certificate state. Do not publish the backend or database ports to the host. The compose file publishes only 80/443.

Check the backend's internal health from inside the container:

```bash
docker compose --env-file .env.production exec backend \
  wget -qO- http://127.0.0.1:8080/actuator/health/readiness
```

The `/actuator` path is not routed by the public Nginx configuration. The health response never exposes component details.

## 4. Database access and schema

Use separate managed DB identities:

- Runtime identity: only the DML permissions needed by the application.
- Migration identity: temporary DDL permissions used only by a reviewed deployment migration.

Never run the application with MySQL root credentials. Do not set production `spring.jpa.hibernate.ddl-auto=update`; Phase 8 sets it to `validate`. Because the repository does not yet contain a complete versioned baseline for every existing entity/table, **do not point a new empty production database at this image**. Generate and review the migration baseline against a disposable copy of the known-good application schema before launch.

## 5. Backups and restore drills

Install the MySQL client tools on an operations host or scheduled backup runner. Use the same managed database host, port, database name and user with TLS:

```bash
export DB_HOST='mysql.example.com'
export DB_PORT='3306'
export DB_NAME='apniko'
export DB_USERNAME='apniko_backup'
export DB_PASSWORD_FILE='./secrets/db_password.txt'
export DB_SSL_MODE='VERIFY_IDENTITY'
export BACKUP_DIR='./backups'
bash scripts/backup-production-db.sh
```

The script uses a consistent single-transaction dump, stores the file with owner-only permissions, validates gzip output and writes a SHA-256 checksum. Copy backups to separate encrypted/offsite storage and retain them according to the business recovery policy. A backup left only on the production host is not a disaster-recovery plan.

A restore is destructive and requires two explicit confirmations:

```bash
export BACKUP_FILE='./backups/apniko-YYYYMMDDTHHMMSSZ.sql.gz'
export CONFIRM_RESTORE='YES'
export CONFIRM_DATABASE_NAME='apniko'
bash scripts/restore-production-db.sh
```

Before restoring, take a fresh backup of the target, verify the selected database name and rehearse the restore in staging. Schedule a monthly restore drill and record measured RPO/RTO.

## 6. Authentication abuse controls

The backend applies a fixed-window limiter to login (default 5 attempts per normalized email and client address per 60 seconds) and public registration (default 3 attempts per client address per 60 seconds). Tune with `AUTH_LOGIN_MAX_ATTEMPTS`, `AUTH_REGISTRATION_MAX_ATTEMPTS` and `AUTH_RATE_LIMIT_WINDOW_SECONDS`. Responses are HTTP 429 with a `Retry-After` header.

The current limiter is bounded in-memory state on one backend instance. Keep production to one backend replica until a shared atomic limiter (such as Redis) or a trusted API gateway has been configured. Do not trust arbitrary client-provided forwarded headers; only the private Caddy → Nginx → backend proxy chain should be able to reach the API container.

Passwords for new customer registrations and administrator-provisioned accounts must meet the same UTF-8-byte-aware 12–72-byte policy. Existing passwords are not silently rewritten; users should change them through a reviewed password-change/reset process before introducing a stricter account migration policy.

## 7. Monitoring and incident response

- Monitor public HTTPS availability and response time from an external uptime service.
- Alert on backend/frontend/Caddy unhealthy status, repeated container restarts, elevated HTTP 5xx, database connection exhaustion and certificate renewal failures.
- Ship stdout/stderr logs to a restricted central logging service; retain access logs and security events according to the data-retention policy. Never log passwords, JWTs, payment secrets or full payment payloads.
- Monitor backup job success, checksum creation, offsite copy status and storage capacity.
- On a suspected credential leak, rotate the affected secret in the runtime secret store, restart affected containers, review access logs and rotate the JWT secret to invalidate outstanding signed JWTs.
- On an application incident, preserve logs, stop unsafe writes if needed, identify the last known-good image tag, restore only after assessing data consistency, and record the incident.

The browser JWT is short-lived and stateless. Logout clears the browser cookie; disabling a user is enforced by checking the account on each authenticated request. A stolen token remains usable until expiry or JWT secret rotation, so keep the lifetime bounded and rotate secrets on compromise.

## 8. Rollback

Keep the current and previous known-good immutable image tags. For a rollback, update `APNIKO_IMAGE_TAG` to the previous tag, rebuild/pull that exact artifact, and run `docker compose up -d`. Do not roll back the database schema unless a separately reviewed backward-compatible migration plan exists. Prefer forward-fix for additive schema changes.

## 9. Release completion record

For every release, record the Git commit, image tags, migration version, approver, CI run links, backup ID, smoke-test result, rollback image tag and deployment timestamp. A green CI run does not authorize production deployment by itself.
