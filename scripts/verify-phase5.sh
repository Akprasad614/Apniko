#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

required_files=(
  "frontend/package.json"
  "frontend/index.html"
  "frontend/vite.config.ts"
  "frontend/src/app/App.tsx"
  "frontend/src/auth/AuthProvider.tsx"
  "frontend/src/lib/api.ts"
  "frontend/src/lib/roles.ts"
  "frontend/src/styles/tokens.css"
  "frontend/src/pages/HomePage.tsx"
  "frontend/src/pages/LoginPage.tsx"
  "frontend/src/pages/RegisterPage.tsx"
  ".github/workflows/frontend-ci.yml"
  ".github/dependabot.yml"
  "PRODUCT.md"
  "DESIGN.md"
  "FRONTEND_RULES.md"
  "SECURITY-AUTOMATION-PLAN.md"
)

for file in "${required_files[@]}"; do
  [[ -s "$file" ]] || { echo "Missing or empty required file: $file" >&2; exit 1; }
done

grep -Fq '"node": "^22.13.0 || >=24.0.0"' frontend/package.json
grep -Fq "'/api/auth/register'" frontend/src/auth/AuthProvider.tsx
grep -Fq "role: 'CUSTOMER'" frontend/src/auth/AuthProvider.tsx
grep -Fq 'CUSTOMER-only' PRODUCT.md
grep -Fq 'npm ci --no-audit --no-fund' .github/workflows/frontend-ci.yml
grep -Fq 'npm run lint' .github/workflows/frontend-ci.yml
grep -Fq 'npm run typecheck' .github/workflows/frontend-ci.yml
grep -Fq 'npm run test:ci' .github/workflows/frontend-ci.yml
grep -Fq 'npm run build' .github/workflows/frontend-ci.yml
grep -Fq 'package-ecosystem: npm' .github/dependabot.yml
grep -Fq 'directory: "/frontend"' .github/dependabot.yml

echo "Phase 5 static verification passed: React/TypeScript/Vite foundation, customer-only registration, role routes, design system, CI and npm Dependabot are present."
