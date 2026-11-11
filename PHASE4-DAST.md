# Apniko Phase 4 — Dynamic security baseline

## What this adds

This phase introduces an OWASP ZAP baseline scan for the Apniko backend. The workflow starts the Spring Boot application inside GitHub Actions, points it at a fresh MySQL service/database named `localcart_dast`, and scans the local Swagger UI entry point at `http://127.0.0.1:8080/swagger-ui/index.html`.

The scan runs on pull requests that change backend code/configuration, matching pushes to `main`, a weekly schedule, and manual dispatch. The ZAP HTML, JSON, and Markdown reports are uploaded as a 14-day Actions artifact and summarized in the workflow run.

## Scan mode and policy

- ZAP Baseline spiders the target and waits for passive scanning. It does not perform active attack payloads.
- `-I` makes warning-level findings report-only in this initial baseline. A scanner execution error still fails the step. Findings are not silently suppressed; they remain in the reports for triage.
- The workflow uses ZAP's `--autooff` compatibility mode rather than the Automation Framework because the Framework's generated summary file failed on this runner. The packaged baseline scan still performs spidering and passive analysis; no active attack scan is enabled.
- Report filenames are relative to the mounted `/zap/wrk` directory, and the job explicitly verifies the HTML, JSON, and Markdown files before uploading the artifact.
- After reviewing the first reports, selected ZAP rule IDs can be promoted to a blocking policy through a reviewed configuration file in a later change.
- This workflow does not run ZAP Full Scan, ZAP API Scan, Nuclei, or an AI pentest agent.

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
