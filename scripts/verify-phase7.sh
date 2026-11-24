#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

test -s frontend/playwright.config.mjs
test -s frontend/e2e/phase7.spec.mjs
test -s scripts/install-phase7-tooling.sh
test -s .github/workflows/phase7-e2e.yml
test -s PHASE7-STAGING-VERIFICATION.md

node --check frontend/playwright.config.mjs
node --check frontend/e2e/phase7.spec.mjs
grep -Fq '@playwright/test@1.65.1' scripts/install-phase7-tooling.sh
grep -Fq '@axe-core/playwright@4.14.0' scripts/install-phase7-tooling.sh
grep -Fq 'localcart_phase7' .github/workflows/phase7-e2e.yml
grep -Fq 'localcart_phase7' frontend/playwright.config.mjs
grep -Fq 'checkA11y' frontend/e2e/phase7.spec.mjs
grep -Fq 'checkMobile' frontend/e2e/phase7.spec.mjs
grep -Fq 'ORDER #' frontend/e2e/phase7.spec.mjs

# No arbitrary external target or production database is accepted by this job.
if grep -E 'jdbc:mysql://[^[:space:]"]*(prod|production)' \
  .github/workflows/phase7-e2e.yml frontend/playwright.config.mjs; then
  echo "Phase 7 safety check failed: production-like DB URL found." >&2
  exit 1
fi

echo "Phase 7 verifier passed: pinned browser/a11y tooling, localhost application and disposable MySQL."
