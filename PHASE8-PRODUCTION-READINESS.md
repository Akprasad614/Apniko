# Phase 8 — production readiness and release controls

**Implementation status:** application and infrastructure-as-code preparation is implemented on `feature/phase-8-production-readiness`.  
**Release status:** not deployed. Domain, hosting account, production secrets, verified schema migration baseline and GitHub ruleset activation require owner-controlled environment setup.

## Delivered in this phase

### Authentication and browser security

- Replaced persistent `sessionStorage` JWT storage with in-memory UI session state. Production browser authentication uses an HttpOnly, Secure, SameSite=Lax `APNIKO_SESSION` cookie.
- Added a CSRF bootstrap endpoint backed by Spring Security's `CookieCsrfTokenRepository`. Mutating browser API requests obtain the token and send `X-XSRF-TOKEN`; the Razorpay webhook remains exempt because it uses its own provider signature validation.
- Added authenticated session restoration and a CSRF-protected logout endpoint.
- Production profile accepts the session cookie, not a JavaScript-readable bearer token. Bearer mode remains explicit for isolated CI fixtures/API regression tests only.
- Added configurable JWT lifetime and a minimum 32-byte secret guard.
- Added bounded fixed-window login and registration rate limits keyed by client address and normalized email, with HTTP 429 and `Retry-After` responses.
- Applied one UTF-8 byte-aware password policy to customer registration and administrator-provisioned accounts; it enforces 12–72 bytes and the required character classes before BCrypt.
- Added pessimistic write locks for payment rows used by verification, COD collection, refunds and provider webhook state transitions; checkout also locks the owning order.
- Changed the profile endpoint to return an explicit `UserResponse` DTO instead of a managed JPA user object.
- Authenticated users are reloaded and checked for active status on each request.

### Production application settings

- Added `application-prod.properties` with mandatory external DB/JWT secrets, TLS JDBC URL, bounded connection pool, Hibernate `validate`, SQL logging disabled, safe error responses, graceful shutdown and private Swagger/OpenAPI disablement.
- Added Spring Boot Actuator liveness/readiness probes with component details hidden.
- Production authentication cookies are always Secure; forwarded headers are enabled for the TLS reverse proxy.

### Runtime and network

- Added multi-stage Docker builds for Spring Boot and the Vite frontend.
- Added Nginx SPA routing and same-origin `/api` reverse proxy. The backend port and Actuator endpoints are not exposed publicly.
- Added Caddy with automatic HTTPS, HSTS, CSP, content-type/referrer/permissions headers and compressed responses.
- Added a Compose deployment using an external managed MySQL database, Docker secret files for DB/JWT credentials, health checks and persistent Caddy certificate state.
- Added deployment environment example with placeholders only; no live secret values are committed.

### Backups, restore and operational controls

- Added a TLS-verified, consistent MySQL backup script with gzip/checksum/owner-only file permissions.
- Added a guarded restore script requiring both `CONFIRM_RESTORE=YES` and an exact database-name confirmation.
- Added operations runbook for deployment, secrets, database privileges, backups, restore drills, monitoring, incident handling and image rollback.
- Added exact GitHub main-branch protection configuration and required-check names.

## Verification and release gates

The Phase 8 verifier and dedicated CI workflow validate:

- No JWT is written to browser storage.
- Cookie-only auth and CSRF configuration are present.
- Production DB/JWT values are external and the schema mode is `validate`.
- Health probes, Docker/Compose, TLS proxy, security headers and secret mounts exist.
- Backup/restore scripts pass Bash syntax and destructive-restore guards.
- Frontend lint, typecheck, unit tests and production build pass.
- Backend Maven verification/tests pass against disposable MySQL.
- Playwright end-to-end order flow, accessibility and responsive checks pass in the isolated CI database.

## Owner-controlled items not executable from this repository

These are not silently marked complete:

1. **Hosting/DNS:** no production host, DNS zone or deployment account is linked to the repository. The included Compose stack can be installed on a chosen host once the domain resolves and 80/443 are reachable.
2. **Production secrets:** populate `secrets/db_password.txt`, `secrets/jwt_secret.txt` and `.env.production` on the host. Do not send these values in chat or commit them.
3. **Schema migration baseline:** the project currently relies on Hibernate `update` in development and does not contain a complete reviewed versioned migration baseline. Build and review that baseline against a known-good copy before creating a production database. Production deliberately refuses automatic schema changes.
4. **GitHub ruleset:** apply the settings in `MAIN-BRANCH-PROTECTION.md` from repository Settings and verify them with a test PR. The connected GitHub file/PR capability does not expose ruleset administration.
5. **External monitoring and offsite backups:** select the provider, configure alert contacts/retention, and prove a restore drill.

Phase 8 code preparation is complete when the Phase 8 checks pass. A public production release remains gated on the above owner-controlled steps; no production environment or customer data has been touched.
