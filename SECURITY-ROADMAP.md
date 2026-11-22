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

## Phase 6 — Supply-chain security (completed in this change)

- Inspect every workflow in `.github/workflows` and pin every third-party GitHub Action and reusable workflow to a verified full 40-character commit SHA.
- Retain release-version comments beside pins so Dependabot can propose updates.
- Add `scripts/verify-workflow-pins.py` and its GitHub Actions workflow to reject mutable third-party GitHub Action and reusable-workflow references on relevant pushes and pull requests.
- Keep Dependabot's weekly monitoring for GitHub Actions, Maven, and npm dependencies in `.github/dependabot.yml`.

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

## Phase 7 — Backend security (planned)

- Continue hardening backend controls after review of Phase 6.

## Phase 8 — Frontend security (planned)

- Continue hardening frontend controls after review of Phase 6.

## Phase 9 — Database and secrets security (planned)

- Continue hardening database and secret-management controls after review of Phase 6.

## Phase 10 — Production security (planned)

- Continue hardening production and release controls after review of Phase 6.
