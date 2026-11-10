#!/usr/bin/env bash
set -euo pipefail

WORKFLOW=".github/workflows/dast-zap-baseline.yml"
PLAN="SECURITY-AUTOMATION-PLAN.md"
DOC="PHASE4-DAST.md"

for file in "$WORKFLOW" "$PLAN" "$DOC"; do
  if [[ ! -f "$file" ]]; then
    echo "Required Phase 4 file is missing: $file" >&2
    exit 1
  fi
done

grep -Fq 'image: mysql:8.4' "$WORKFLOW"
grep -Fq 'MYSQL_DATABASE: localcart_dast' "$WORKFLOW"
grep -Fq 'jdbc:mysql://127.0.0.1:3306/localcart_dast' "$WORKFLOW"
grep -Fq 'openssl rand -base64 48' "$WORKFLOW"
grep -Fq 'zap-baseline.py' "$WORKFLOW"
grep -Fq 'http://127.0.0.1:8080/swagger-ui/index.html' "$WORKFLOW"
grep -Fq ' -I' "$WORKFLOW"
grep -Fq -- '--network host' "$WORKFLOW"

if grep -Eq 'https?://[^ ]*(prod|production)' "$WORKFLOW"; then
  echo "Unexpected production-looking target in DAST workflow." >&2
  exit 1
fi


echo "Phase 4 static verification passed: isolated DB, local target, passive ZAP mode, ephemeral JWT, and report artifacts are configured."
