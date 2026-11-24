#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

required=(
  "src/main/resources/application-prod.properties"
  "src/main/java/com/localcart/controller/AuthController.java"
  "src/main/java/com/localcart/security/SecurityConfig.java"
  "src/main/java/com/localcart/security/JwtAuthenticationFilter.java"
  "src/main/java/com/localcart/security/AuthenticationRateLimiter.java"
  "src/main/java/com/localcart/security/StrongPasswordValidator.java"
  "src/main/java/com/localcart/repository/PaymentRepository.java"
  "src/test/java/com/localcart/security/AuthenticationRateLimiterTest.java"
  "src/test/java/com/localcart/security/StrongPasswordValidatorTest.java"
  "src/test/java/com/localcart/controller/AuthControllerCookieTest.java"
  "frontend/src/auth/AuthProvider.tsx"
  "frontend/src/lib/api.ts"
  "Dockerfile"
  "frontend/Dockerfile"
  "frontend/nginx.conf"
  "frontend/.dockerignore"
  "scripts/verify-workflow-pins.py"
  ".github/workflows/workflow-pin-verification.yml"
  "Caddyfile"
  "compose.yaml"
  ".env.production.example"
  "MAIN-BRANCH-PROTECTION.md"
  "OPERATIONS-RUNBOOK.md"
  "PHASE8-PRODUCTION-READINESS.md"
  "scripts/docker-entrypoint.sh"
  "scripts/backup-production-db.sh"
  "scripts/restore-production-db.sh"
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { echo "Missing or empty Phase 8 file: $file" >&2; exit 1; }
done

AUTH="frontend/src/auth/AuthProvider.tsx"
API="frontend/src/lib/api.ts"
CONTROLLER="src/main/java/com/localcart/controller/AuthController.java"
FILTER="src/main/java/com/localcart/security/JwtAuthenticationFilter.java"
SECURITY="src/main/java/com/localcart/security/SecurityConfig.java"
PROD="src/main/resources/application-prod.properties"

# The browser must never persist a bearer token in Web Storage.
if grep -Eiq 'sessionStorage|localStorage' "$AUTH" "$API"; then
  echo "Phase 8 auth check failed: browser storage reference remains." >&2
  exit 1
fi
grep -Fq 'httpOnly(true)' "$CONTROLLER"
grep -Fq 'secure(secureCookie)' "$CONTROLLER"
grep -Fq '.sameSite("Lax")' "$CONTROLLER"
grep -Fq 'CookieCsrfTokenRepository.withHttpOnlyFalse()' "$SECURITY"
grep -Fq 'X-XSRF-TOKEN' "$API"
grep -Fq "apiRequest<AuthResponse>('/api/auth/session')" "$AUTH"
grep -Fq 'cookieName.equals(cookie.getName())' "$FILTER"
grep -Fq 'rateLimiter.checkLoginAllowed' "$CONTROLLER"
grep -Fq 'rateLimiter.checkRegistrationAllowed' "$CONTROLLER"
grep -Fq 'BCRYPT_MAXIMUM_UTF8_BYTES = 72' src/main/java/com/localcart/security/StrongPasswordValidator.java
grep -Fq 'HttpStatus.TOO_MANY_REQUESTS' src/main/java/com/localcart/exception/GlobalExceptionHandler.java
grep -Fq 'HttpHeaders.RETRY_AFTER' src/main/java/com/localcart/exception/GlobalExceptionHandler.java
grep -Fq '@Lock(LockModeType.PESSIMISTIC_WRITE)' src/main/java/com/localcart/repository/PaymentRepository.java
grep -Fq 'ResponseEntity<UserResponse> getProfile()' src/main/java/com/localcart/controller/ProfileController.java
grep -Fq '"/api/auth/csrf"' "$SECURITY"
grep -Fq '"/api/auth/logout"' "$SECURITY"
grep -Fq '@StrongPassword' src/main/java/com/localcart/dto/RegisterRequest.java
grep -Fq '@StrongPassword' src/main/java/com/localcart/dto/CreateManagedUserRequest.java
grep -Fq 'MAX_TRACKED_KEYS = 10_000' src/main/java/com/localcart/security/AuthenticationRateLimiter.java
grep -Fq 'security.auth.rate-limit.login.max-attempts' src/main/resources/application.properties
grep -Fq 'checkLoginAllowed' "$CONTROLLER"
grep -Fq 'checkRegistrationAllowed' "$CONTROLLER"
grep -Fq 'findByIdForUpdate' src/main/java/com/localcart/repository/OrderRepository.java

# The production profile must fail closed for schema drift and secret exposure.
grep -Fq 'spring.jpa.hibernate.ddl-auto=validate' "$PROD"
grep -Fq 'spring.jpa.show-sql=false' "$PROD"
grep -Fq 'server.error.include-stacktrace=never' "$PROD"
grep -Fq 'springdoc.swagger-ui.enabled=false' "$PROD"
grep -Fq 'management.endpoint.health.show-details=never' "$PROD"
grep -Fq 'app.auth.cookie.secure=true' "$PROD"
grep -Fq 'DB_URL' "$PROD"
grep -Fq 'JWT_SECRET' "$PROD"

# The public compose ingress is Caddy only; backend and frontend are not host-published.
grep -Fq '80:80' compose.yaml
grep -Fq '443:443' compose.yaml
if grep -Eq '^[[:space:]]+ports:' compose.yaml; then
  count="$(grep -Ec '^[[:space:]]+ports:' compose.yaml)"
  [[ "$count" -eq 1 ]] || {
    echo "Only the Caddy ingress may publish host ports." >&2
    exit 1
  }
fi
grep -Fq 'DB_PASSWORD_FILE: /run/secrets/db_password' compose.yaml
grep -Fq 'JWT_SECRET_FILE: /run/secrets/jwt_secret' compose.yaml
grep -Fq 'sslMode=VERIFY_IDENTITY' .env.production.example
grep -Fq 'Strict-Transport-Security' Caddyfile
grep -Fq 'Content-Security-Policy' Caddyfile
grep -Fq 'X-Content-Type-Options' Caddyfile
grep -Fq 'Referrer-Policy' Caddyfile

# Backup/restore utilities must be syntactically valid and require explicit restore confirmation.
bash -n scripts/backup-production-db.sh scripts/restore-production-db.sh scripts/verify-phase8.sh
sh -n scripts/docker-entrypoint.sh
grep -Fq 'CONFIRM_RESTORE' scripts/restore-production-db.sh
grep -Fq 'CONFIRM_DATABASE_NAME' scripts/restore-production-db.sh
grep -Fq 'VERIFY_IDENTITY' scripts/backup-production-db.sh
grep -Fq 'gzip -t' scripts/restore-production-db.sh

grep -Fq 'springdoc.swagger-ui.version=5.34.1' src/main/resources/application.properties
grep -Fq 'new SwaggerUiSecurityHeadersWriter()' "$SECURITY"
grep -Fq '.addHeaderWriter(new SwaggerUiSecurityHeadersWriter())' "$SECURITY"

echo "Phase 8 verifier passed: cookie/CSRF auth, production profile, private runtime topology, TLS headers and guarded recovery scripts."
