#!/usr/bin/env bash
set -euo pipefail

APP="frontend/src/app/App.tsx"
FEATURE="frontend/src/features/customer/CustomerStorefront.tsx"
HELPERS="frontend/src/features/customer/marketplace.ts"
STYLES="frontend/src/features/customer/customer-storefront.css"
TESTS="frontend/src/features/customer/marketplace.test.ts"

for file in "$APP" "$FEATURE" "$HELPERS" "$STYLES" "$TESTS"; do
  if [[ ! -f "$file" ]]; then
    echo "Required customer storefront file is missing: $file" >&2
    exit 1
  fi
done

grep -Fq '<CustomerStorefront />' "$APP"
grep -Fq "'/api/customer/marketplace/shops'" "$FEATURE"
grep -Fq "'/api/customer/marketplace/categories'" "$FEATURE"
grep -Fq 'buildMarketplaceSearchPath' "$FEATURE"
grep -Fq 'api/customer/cart/${selectedShopId}/items' "$FEATURE"
grep -Fq 'api/customer/cart/${selectedShopId}/items/${productId}' "$FEATURE"
grep -Fq 'role="alert"' "$FEATURE"
grep -Fq 'aria-pressed' "$FEATURE"
grep -Fq 'prefers-reduced-motion' 'frontend/src/styles/global.css'

if grep -Eiq 'demo|mockData|fakeProducts|sampleProducts' "$FEATURE"; then
  echo "Customer storefront must use live API data, not hardcoded demo data." >&2
  exit 1
fi

echo "Phase 6 verification passed: customer route uses live marketplace and per-shop basket APIs; no hardcoded demo catalog was detected."
