#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "Phase 13 integration/reliability check failed: $1" >&2
  exit 1
}

required=(
  "src/main/java/com/localcart/security/SecurityConfig.java"
  "src/main/java/com/localcart/controller/AuthController.java"
  "src/main/java/com/localcart/controller/PaymentController.java"
  "src/main/java/com/localcart/service/PaymentService.java"
  "src/main/java/com/localcart/service/RazorpayGatewayClient.java"
  "src/main/java/com/localcart/repository/OrderRepository.java"
  "src/main/java/com/localcart/repository/ShopInventoryRepository.java"
  "src/main/java/com/localcart/service/KhathaService.java"
  "src/main/java/com/localcart/repository/KhathaAccountRepository.java"
  "src/main/java/com/localcart/repository/KhathaLedgerEntryRepository.java"
  "src/main/java/com/localcart/entity/KhathaLedgerEntry.java"
  "src/main/java/com/localcart/controller/KhathaController.java"
  "src/main/resources/application-prod.properties"
  "compose.yaml"
  "scripts/backup-production-db.sh"
  "scripts/restore-production-db.sh"
  "src/test/java/com/localcart/service/KhathaServiceTest.java"
  "src/test/java/com/localcart/service/PaymentServiceTest.java"
  "src/test/java/com/localcart/integration/OrderPersistenceIntegrationTest.java"
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || fail "missing required integration/security file: $file"
done

# Re-run the phase-level guardrails that protect catalogue, authentication,
# production configuration and geolocation while features are integrated.
bash scripts/verify-phase4-text-only.sh
bash scripts/verify-phase5-admin-catalogue.sh
bash scripts/verify-phase8.sh
bash scripts/verify-phase10-geolocation.sh

SECURITY="src/main/java/com/localcart/security/SecurityConfig.java"
AUTH="src/main/java/com/localcart/controller/AuthController.java"
PAYMENT="src/main/java/com/localcart/service/PaymentService.java"
GATEWAY="src/main/java/com/localcart/service/RazorpayGatewayClient.java"
KHATHA="src/main/java/com/localcart/service/KhathaService.java"
ACCOUNT_REPO="src/main/java/com/localcart/repository/KhathaAccountRepository.java"
LEDGER_REPO="src/main/java/com/localcart/repository/KhathaLedgerEntryRepository.java"
LEDGER="src/main/java/com/localcart/entity/KhathaLedgerEntry.java"
PROD="src/main/resources/application-prod.properties"

# Every role namespace remains authenticated and role-scoped; CSRF must never
# be globally disabled to make API tests convenient.
grep -Fq '.requestMatchers("/api/admin/**").hasRole("ADMIN")' "$SECURITY" || fail "admin route role boundary missing"
grep -Fq '.requestMatchers("/api/shopkeeper/**").hasRole("SHOPKEEPER")' "$SECURITY" || fail "shopkeeper route role boundary missing"
grep -Fq '.requestMatchers("/api/delivery/**").hasRole("DELIVERY_PARTNER")' "$SECURITY" || fail "delivery route role boundary missing"
grep -Fq '.requestMatchers("/api/customer/**").hasRole("CUSTOMER")' "$SECURITY" || fail "customer route role boundary missing"
! grep -Eq 'csrf\.disable\(' "$SECURITY" || fail "global CSRF disable detected"
grep -Fq 'httpOnly(true)' "$AUTH" || fail "session cookie is not HttpOnly"

# Payment operations must lock the order, verify ownership, and use a provider
# endpoint allowlist. The tests exercise the gateway contract without live keys.
grep -Fq 'findByIdForUpdate(orderId)' "$PAYMENT" || fail "payment order lock missing"
grep -Fq 'You cannot pay for another customer' "$PAYMENT" || fail "payment ownership check missing"
grep -Fq 'api.razorpay.com' "$GATEWAY" || fail "official Razorpay endpoint allowlist missing"
grep -Fq 'HmacSHA256' "$PAYMENT" || fail "payment signature verification missing"

# Khatha accounting invariants: unique source identity, pessimistic account
# locks, transactional service operations, and India-local due-date rules.
grep -Fq 'uk_khatha_ledger_source' "$LEDGER" || fail "ledger source uniqueness constraint missing"
grep -Fq 'findByIdForUpdate' "$ACCOUNT_REPO" || fail "Khatha account row lock missing"
grep -Fq 'findByShopAndCustomerForUpdate' "$ACCOUNT_REPO" || fail "shop/customer account lock missing"
grep -Fq 'existsByAccountIdAndSourceTypeAndSourceId' "$LEDGER_REPO" || fail "ledger idempotency lookup missing"
grep -Fq 'Asia/Kolkata' "$KHATHA" || fail "Khatha business timezone missing"
grep -Fq 'confirmRepayment' "$KHATHA" || fail "shopkeeper repayment verification missing"
grep -Fq 'lockAccount' "$KHATHA" || fail "locked account mutation path missing"
if grep -Eiq 'delete(All|By|\()|@Modifying|@Query.*(update|delete)' "$LEDGER_REPO"; then
  fail "ledger repository exposes a mutation/deletion query"
fi
if grep -Eq '@DeleteMapping' src/main/java/com/localcart/controller/KhathaController.java; then
  fail "Khatha API exposes a delete endpoint"
fi
grep -Fq 'ORDER_REVERSAL' "$KHATHA" || fail "order cancellation reversal accounting missing"

# Production must fail closed on schema drift and keep diagnostics private.
grep -Fq 'spring.jpa.hibernate.ddl-auto=validate' "$PROD" || fail "production Hibernate schema validation missing"
grep -Fq 'server.error.include-stacktrace=never' "$PROD" || fail "production stack traces are exposed"
grep -Fq 'management.endpoint.health.show-details=never' "$PROD" || fail "health details are exposed"
grep -Fq 'DB_PASSWORD_FILE: /run/secrets/db_password' compose.yaml || fail "database password is not mounted as a secret"
grep -Fq 'JWT_SECRET_FILE: /run/secrets/jwt_secret' compose.yaml || fail "JWT secret is not mounted as a secret"

# Recovery scripts and all GitHub Actions references are checked for drift.
bash -n scripts/backup-production-db.sh scripts/restore-production-db.sh
python3 scripts/verify-workflow-pins.py

echo "Phase 13 integration/reliability verification passed."
