#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

required_files=(
  "PHASE14-STAGING-READINESS.md"
  "DEPLOYMENT-GUIDE.md"
  "PAYMENTS-KHATHA-STAGING-TEST-PLAN.md"
  "compose.yaml"
  "Caddyfile"
  ".env.production.example"
  "src/main/resources/application-prod.properties"
  "scripts/backup-production-db.sh"
  "scripts/restore-production-db.sh"
  "scripts/docker-entrypoint.sh"
)
for file in "${required_files[@]}"; do
  test -s "$file" || { echo "Missing required file: $file" >&2; exit 1; }
done

bash -n scripts/backup-production-db.sh
bash -n scripts/restore-production-db.sh
bash -n scripts/docker-entrypoint.sh

grep -q '^spring.jpa.hibernate.ddl-auto=validate$' src/main/resources/application-prod.properties
grep -q 'sslMode=VERIFY_IDENTITY' .env.production.example
grep -q 'springdoc.api-docs.enabled=false' src/main/resources/application-prod.properties
grep -q 'management.endpoint.health.show-details=never' src/main/resources/application-prod.properties
grep -q 'CONFIRM_RESTORE' scripts/restore-production-db.sh
grep -q 'CONFIRM_DATABASE_NAME' scripts/restore-production-db.sh
grep -q 'rights verified' README.md || grep -q 'commercial-use' README.md

if find src/main/resources -maxdepth 3 -type f \\( -path '*/db/migration/*' -o -path '*/flyway/*' \\) 2>/dev/null | grep -q .; then
  echo "Migration files exist; require explicit human schema review before deployment."
else
  echo "No migration baseline exists: safe hold confirmed; no guessed SQL generated."
fi

echo "Phase 14 repository readiness checks passed (live staging acceptance remains owner-run)."
