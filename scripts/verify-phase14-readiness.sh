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
  "src/main/resources/application.properties"
  "src/main/resources/application-prod.properties"
  "src/main/resources/db/migration/V1__legacy_localcart_schema_baseline.sql"
  "src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql"
  "DATABASE-MIGRATION-BASELINE.md"
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

grep -q '^spring.jpa.hibernate.ddl-auto=validate$' src/main/resources/application.properties
grep -q '^spring.jpa.hibernate.ddl-auto=validate$' src/main/resources/application-prod.properties
grep -q '^spring.flyway.enabled=false$' src/main/resources/application-prod.properties
grep -q '^spring.flyway.baseline-on-migrate=false$' src/main/resources/application-prod.properties
grep -q '^spring.flyway.baseline-on-migrate=false$' src/main/resources/application.properties
grep -q 'spring-boot-starter-flyway' pom.xml
grep -q 'flyway-mysql' pom.xml
grep -q 'flyway-maven-plugin' pom.xml
grep -q 'CREATE TABLE khatha_accounts' src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql
grep -q 'CREATE TABLE khatha_ledger_entries' src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql
grep -q 'CREATE TABLE khatha_repayments' src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql
grep -q 'CREATE TABLE khatha_audit_logs' src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql
grep -q 'CREATE TABLE khatha_balance_allocations' src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql
if grep -Eiq 'DROP[[:space:]]+TABLE|DROP[[:space:]]+COLUMN' src/main/resources/db/migration/V1__legacy_localcart_schema_baseline.sql src/main/resources/db/migration/V2__align_schema_with_current_apniko_entities.sql; then
  echo "Database migrations must not drop existing objects." >&2
  exit 1
fi
grep -q 'sslMode=VERIFY_IDENTITY' .env.production.example
grep -q 'springdoc.api-docs.enabled=false' src/main/resources/application-prod.properties
grep -q 'management.endpoint.health.show-details=never' src/main/resources/application-prod.properties
grep -q 'CONFIRM_RESTORE' scripts/restore-production-db.sh
grep -q 'CONFIRM_DATABASE_NAME' scripts/restore-production-db.sh

echo "Versioned Flyway baseline and forward upgrade are present; runtime schema management is validation-only."
echo "Phase 14 repository readiness checks passed (live staging acceptance remains owner-run)."
