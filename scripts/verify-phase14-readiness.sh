#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

required_files=(
  "PHASE14-STAGING-READINESS.md"
  "DEPLOYMENT-GUIDE.md"
  "PAYMENTS-KHATHA-STAGING-TEST-PLAN.md"
  "RETAIL-CATALOGUE-LICENSING-HANDOFF.md"
  "compose.yaml"
  "Caddyfile"
  ".env.production.example"
  "src/main/resources/application-prod.properties"
  "scripts/backup-production-db.sh"
  "scripts/restore-production-db.sh"
  "scripts/docker-entrypoint.sh"
  "scripts/staging-smoke-test.sh"
)
for file in "${required_files[@]}"; do
  test -s "$file" || { echo "Missing required file: $file" >&2; exit 1; }
done

bash -n scripts/backup-production-db.sh
bash -n scripts/restore-production-db.sh
bash -n scripts/docker-entrypoint.sh
bash -n scripts/staging-smoke-test.sh

grep -q '^spring.jpa.hibernate.ddl-auto=validate$' src/main/resources/application-prod.properties
grep -q 'sslMode=VERIFY_IDENTITY' .env.production.example
grep -q 'springdoc.api-docs.enabled=false' src/main/resources/application-prod.properties
grep -q 'management.endpoint.health.show-details=never' src/main/resources/application-prod.properties
grep -q 'CONFIRM_RESTORE' scripts/restore-production-db.sh
grep -q 'CONFIRM_DATABASE_NAME' scripts/restore-production-db.sh

echo "No schema baseline is supplied by this change; operator must establish and review it from the real schema."
echo "Phase 14 repository readiness checks passed (live staging acceptance remains owner-run)."
