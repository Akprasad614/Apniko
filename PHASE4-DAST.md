# Apniko Phase 4 — Dynamic security baseline

## What this adds

This phase introduces an OWASP ZAP baseline scan for the Apniko backend. The workflow starts the Spring Boot application inside GitHub Actions, points it at a fresh MySQL service/database named `localcart_dast`, and scans the local Swagger UI entry point at `http://127.0.0.1:8080/swagger-ui/index.html`.

The scan runs on pull requests that change backend code/configuration, matching pushes to `main`, a weekly schedule, and manual dispatch. The ZAP HTML, JSON, and Markdown reports are uploaded as a 14-day Actions artifact and summarized in the workflow run.

## Scan mode and policy

- ZAP Baseline spiders the target and waits for passive scanning. It does not perform active attack payloads.
- The workflow does not pass `-I`: warning-level passive findings fail the job so new issues cannot silently pass. Informational observations remain report-only in the ZAP report. Scanner execution errors also fail the step.
- The workflow uses ZAP's `--autooff` compatibility mode rather than the Automation Framework because the Framework's generated summary file failed on this runner. The packaged baseline scan still performs spidering and passive analysis; no active attack scan is enabled.
- Report filenames are relative to the mounted `/zap/wrk` directory, and the job explicitly verifies the HTML, JSON, and Markdown files before uploading the artifact.
- After reviewing the first reports, selected ZAP rule IDs can be promoted to a blocking policy through a reviewed configuration file in a later change.
- This workflow does not run ZAP Full Scan, ZAP API Scan, Nuclei templates or active penetration-testing services.

## First recorded baseline and open finding

The 22 November 2026 baseline run on the disposable PR environment (workflow run
`45050829870`, ZAP 2.17.0) completed and uploaded its reports. The report showed:

- 0 High alerts and 0 Medium alerts.
- 1 Low alert: **Application Error Disclosure** (rule `90022`) on
  `GET /swagger-ui/index.html`, with HTTP 500 as the evidence.
- 4 Informational `Non-Storable Content` observations on the root, robots,
  sitemap and Swagger UI responses (the application sends `Cache-Control: no-store`).
- The spider reported only 4 endpoints, so this is not broad API route coverage.

The workflow's green conclusion means the scanner completed; it does **not**
mean there were no findings. This workflow now fails on warning-level passive findings; only informational observations remain report-only.
Treat the Swagger UI 500 as an open application issue until a subsequent run
confirms the endpoint returns the intended response and the response is reviewed.
The informational cache observations should remain separate from security
vulnerabilities; do not remove `no-store` from sensitive or user-specific content.

The workflow now captures status codes and response bodies for Swagger UI,
`/v3/api-docs` and the Swagger configuration endpoint, plus a bounded,
password-redacted tail of the disposable app startup log. These diagnostics are
retained with the ZAP reports to make the 500 traceable without using production
data or credentials.

The follow-up diagnostic run `45203543836` captured the server-side cause:
Spring MVC raised `NoResourceFoundException` because `index.html` was not
found under `/swagger-ui/`. The project pins Springdoc `3.2.0` while
overriding the Swagger UI WebJar to `5.34.1`, but did not set the matching
`springdoc.swagger-ui.version` property. Springdoc's
[version override guidance](https://springdoc.org/faq.html) says the explicit
property must match the WebJar version with Springdoc versions before 3.2.1.
Phase 4 now sets `springdoc.swagger-ui.version=5.34.1` in
`application.properties`.

The next diagnostic run `45204796107` verified the correction: Swagger UI,
OpenAPI JSON and Swagger configuration all returned HTTP 200, with no
application exception in the captured log. The former 500 is resolved. That
successful response exposed one Medium CSP finding and four Low browser-header
findings on the Swagger UI document. A dedicated `SwaggerUiSecurityHeadersWriter`
now applies a restrictive CSP, COEP, COOP, CORP and Permissions Policy only
to Swagger UI routes. It deliberately does not impose those policies on
customer/API routes. Unit tests check both the positive Swagger route and the
absence of these route-specific headers on customer routes. The stricter baseline correctly rejected the first header change: ZAP rule
`10055` identified an omitted `form-action` directive (which does not inherit
from `default-src`) and an inline-style allowance. The Swagger-only CSP now
sets `form-action 'self'` and uses `style-src 'self'` without
`unsafe-inline`. The writer tests assert the exact policy and verify the
Swagger-only headers are not applied to customer API routes.

The final blocking baseline on commit
`7f9f9a38bcd9aeaffa3ee10a08f8c44cb84b87b8` (workflow run `45206450967`,
ZAP 2.17.0) passed with **0 High, 0 Medium and 0 Low alerts**. The scanner
reported only two informational categories: Modern Web Application (Swagger UI)
and Non-Storable Content (four `no-store` responses). All three diagnostic
endpoints returned HTTP 200. Keep `no-store`; the informational alert is not
a reason to weaken caching for responses that may contain sensitive or
user-specific data. The spider covered four unauthenticated endpoints only,
so this is a passing baseline for the documented scope, not proof that every
authenticated business flow has been security-tested.

## Isolation and safety

- GitHub Actions creates a disposable MySQL 8.4 service and `localcart_dast` database for each run.
- `SPRING_DATASOURCE_URL` explicitly points to `localcart_dast`; the ordinary local `localcart` database is never referenced.
- The run creates a one-time JWT secret and uses no real account, payment, Razorpay, or production credentials.
- The scanner connects only to the Apniko app bound on the same GitHub-hosted runner. Docker host networking is used so the ZAP container can reach that local app; no externally supplied target URL is accepted.
- The workflow uses read-only repository permissions and uploads reports as artifacts.
- The service container/database is discarded at the end of the job.

## Limitations

This is an unauthenticated passive baseline, not an authenticated business-logic penetration test. It does not exercise every protected endpoint, IDOR path, payment flow, race condition, stock mutation, or role-specific journey. Those require dedicated seeded test identities and carefully scoped authenticated test plans in a later iteration. A clean ZAP baseline is not proof that Apniko is vulnerability-free.

## Manual run

Open GitHub Actions → `Apniko DAST - OWASP ZAP Baseline` → Run workflow. Review the job summary and download the `localcart-zap-baseline-*` artifact.

## Local static check

```bash
bash scripts/verify-phase4.sh
```
