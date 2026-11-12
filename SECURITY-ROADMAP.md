# Apniko Security Roadmap

This is the security roadmap. It is intentionally separate from the application-development roadmap in `SECURITY-AUTOMATION-PLAN.md` so that product delivery phases and security hardening phases are not conflated.

## Phase 1 — Security baseline (completed)

- Establish secure CI permissions, dependency maintenance, and code-scanning foundations.

## Phase 2 — Security visibility (completed)

- Add static security scanning, secret scanning, dependency vulnerability scanning, and SBOM generation.

## Phase 3 — Authorization regression coverage (completed)

- Add tests for authorization, ownership, and business-logic isolation boundaries.

## Phase 4 — Dynamic security baseline (completed)

- Run passive DAST against a disposable CI environment and retain reports for review.

## Phase 5 — Frontend security foundation (completed)

- Establish frontend CI and document the current browser-token storage limitation for a future reviewed authentication design.

## Phase 6 — Supply-chain security (completed)

- Inspect every workflow in `.github/workflows` and pin every third-party GitHub Action and reusable workflow to a verified full 40-character commit SHA.
- Retain release-version comments beside pins so Dependabot can propose updates.
- Add `scripts/verify-workflow-pins.py` and its GitHub Actions workflow to reject mutable third-party GitHub Action and reusable-workflow references on relevant pushes and pull requests.
- Keep Dependabot's weekly monitoring for GitHub Actions, Maven, and npm dependencies.

Verified release mappings:

| Dependency | Release | Verified commit SHA |
| --- | --- | --- |
| `actions/checkout` | `v7` | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-node` | `v6` | `249970729cb0ef3589644e2896645e5dc5ba9c38` |
| `actions/setup-java` | `v6` | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| `actions/upload-artifact` | `v7` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |
| `github/codeql-action` | `v4` | `33d7b85da1a04171e211befe2c739303b9a4c066` |
| `gitleaks/gitleaks-action` | `v3` | `e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e` |
| `google/osv-scanner-action` reusable workflows | `v2.7.0` | `b1d267aea5211fa648d9d442fa64b90dbdcbdd36` |

The release tags above were resolved from the corresponding official GitHub upstream repositories before the pins were applied. Annotated tags were dereferenced to their target commit.

## Phase 7 — Backend security (completed)

- Add a UTF-8 byte-aware strong password policy for new public registrations and administrator-managed account creation. The policy requires at least 12 bytes, enforces upper-case, lower-case, digit, and symbol character classes, and rejects values exceeding BCrypt's 72-byte input limit. Existing accounts retain login compatibility because the new constraint applies when creating accounts, not when authenticating existing passwords.
- Add configurable fixed-window authentication throttling for login and registration. Each login request consumes an attempt atomically against both the client address and normalized account key before authentication; successful logins do not reset the shared IP limit. Return HTTP 429 with a `Retry-After` header when the configured threshold is reached.
- Restrict public authentication access to POST login and registration routes rather than permitting every method under `/api/auth/**`.
- Make JWT expiration configurable while retaining signature verification, expiration validation, and active-account checks.
- Apply pessimistic write locks to payment lookups used by state-changing checkout verification, COD collection, refund, and webhook flows. Retain gateway signature, ownership, amount, and currency verification and idempotent state-transition checks.
- Replace profile and delivery response paths that serialized managed entities with response DTOs to avoid exposing unrelated entity relationships or fields.
- Add or extend tests for password policy boundaries, rate limiting and retry information, JWT tampering and expiry, inactive accounts, authentication responses, payment workflows, and existing Phase 3 authorization regression coverage.

Validation on the Phase 7 pull-request commit:

- GitHub Actions Backend CI passed using Java 21 and the disposable MySQL service; Maven verification and tests completed successfully.
- Workflow-pin verification passed.
- CodeQL passed.
- Semgrep and Gitleaks passed.
- OSV dependency scanning passed.
- OWASP ZAP passive baseline scan passed against the disposable CI instance.
- CycloneDX SBOM generation passed.
- Frontend CI passed.
- Maven dependency submission passed.
- Local test execution on the developer machine was limited by the Byte Buddy/Mockito self-attachment restriction; the full backend test suite was therefore validated on the Java 21 GitHub Actions runner.

Residual deployment consideration:

- The authentication rate limiter uses bounded in-memory state within one application instance. Before running multiple backend instances in production, replace or complement it with a shared rate-limit store or trusted edge/API-gateway enforcement. Do not trust arbitrary client-supplied forwarded-IP headers; configure trusted proxy handling deliberately.

## Phase 8 — Frontend security (planned)

- Review browser token/session handling, session migration compatibility, XSS exposure, dependency health, secure API communication, and role-aware UI behavior. Backend authorization remains authoritative.

## Phase 9 — Database and secrets security (planned)

- Review production database least privilege, schema migration strategy, backup/restore verification, encryption, and credential management.

## Phase 10 — Production security (planned)

- Complete production deployment hardening, shared rate-limit enforcement for multi-instance deployments, secure headers and HTTPS, monitoring, incident response, backups, rollback, and authenticated staging security verification.
