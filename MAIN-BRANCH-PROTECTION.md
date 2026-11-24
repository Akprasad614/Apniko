# Protecting the Apniko `main` branch

This is the exact repository-owner setup for the Phase 8 release gate. It is intentionally documented rather than applied automatically: the connected GitHub capability used for this change can inspect and update files and pull requests, but it does not expose the branch-ruleset administration API. Do not infer that a checked-in document protects a branch.

## Apply in GitHub

Open **Settings → Rules → Rulesets** (or **Settings → Branches → Branch protection rules**) and create an active ruleset for the exact branch `main`.

Recommended settings:

- Require a pull request before merging.
- Require at least **one approval** from someone other than the author.
- Dismiss stale approvals when new commits are pushed.
- Require approval of the most recent reviewable push where available.
- Require all review conversations to be resolved.
- Require status checks to pass before merging; select the names below from the check picker after this workflow has run on a pull request targeting `main`.
- Require branches to be up to date before merging.
- Block force pushes and branch deletion.
- Require linear history if the repository's selected merge method supports it.
- Do not add a broad bypass list. Keep emergency bypass restricted to repository owners and document every use.

Do not enable "require signed commits" or a deployment environment requirement until contributor signing and the deployment workflow are configured and tested.

## Required status checks

Select these job/check names exactly as GitHub presents them for the current workflows:

| Check name | Workflow |
|---|---|
| `Lint, typecheck, test and build frontend` | Apniko Frontend CI |
| `Verify backend and Phase 10 assets` | Apniko Backend CI |
| `Analyze Java source` | Apniko CodeQL |
| `Gitleaks secret scan` | Apniko Static Security Scans |
| `Semgrep security audit` | Apniko Static Security Scans |
| `Check newly introduced dependency vulnerabilities / osv-scan` | Apniko OSV Dependency Scan |
| `Generate Maven dependency inventory` | Apniko CycloneDX SBOM |
| `Production readiness, tests and browser acceptance` | Apniko Phase 8 — production readiness |

The Phase 8 check is deliberately part of the same required set. The passive DAST baseline can also be selected after its check name is confirmed on a main-targeted PR. Do not select checks that are not consistently triggered for every main PR.

## Verify the rule

After saving:

1. Open a harmless test pull request targeting `main`.
2. Confirm all expected checks appear and are required.
3. Confirm GitHub blocks merge while a required check is pending or failing.
4. Confirm GitHub blocks a direct push and force-push to `main`.
5. Record the ruleset URL, rule ID and the person who verified it in the release record.

**Current status:** a ruleset has not been created by this code change. Repository owners must apply and verify it in GitHub settings.
