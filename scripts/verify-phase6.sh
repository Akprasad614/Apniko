#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

required_files=(
  "frontend/src/app/App.tsx"
  "frontend/src/features/customer/CustomerStorefront.tsx"
  "frontend/src/features/customer/CustomerDiscoverySearch.tsx"
  "frontend/src/features/customer/customer-discovery-search.css"
  "frontend/src/features/customer/CustomerCart.tsx"
  "frontend/src/features/customer/CustomerOrders.tsx"
  "frontend/src/features/customer/marketplace.ts"
  "frontend/src/features/customer/marketplace.test.ts"
  "frontend/src/features/shopkeeper/ShopkeeperDashboard.tsx"
  "frontend/src/features/shopkeeper/shopkeeper.ts"
  "frontend/src/features/shopkeeper/shopkeeper.test.ts"
  "frontend/src/features/delivery/DeliveryDashboard.tsx"
  "frontend/src/features/admin/AdminDashboard.tsx"
  "frontend/src/features/shared/operations.css"
  "frontend/src/features/shared/NotificationCenter.tsx"
  "frontend/src/features/shared/notification-center.css"
  "frontend/src/features/shared/notifications.ts"
  "frontend/src/features/shared/notifications.test.ts"
  "frontend/src/styles/global.css"
  "PHASE6-ROLE-BASED-DELIVERY.md"
  "PHASE6-CUSTOMER-STOREFRONT.md"
)

for file in "${required_files[@]}"; do
  [[ -s "$file" ]] || { echo "Missing or empty Phase 6 file: $file" >&2; exit 1; }
done

APP="frontend/src/app/App.tsx"
CUSTOMER="frontend/src/features/customer/CustomerStorefront.tsx"
CART="frontend/src/features/customer/CustomerCart.tsx"
ORDERS="frontend/src/features/customer/CustomerOrders.tsx"
SHOPKEEPER="frontend/src/features/shopkeeper/ShopkeeperDashboard.tsx"
DELIVERY="frontend/src/features/delivery/DeliveryDashboard.tsx"
ADMIN="frontend/src/features/admin/AdminDashboard.tsx"
GLOBAL="frontend/src/styles/global.css"

# Role routing and the live customer shopping journey.
grep -Fq "allowed={['CUSTOMER']}" "$APP"
grep -Fq "allowed={['SHOPKEEPER']}" "$APP"
grep -Fq "allowed={['DELIVERY_PARTNER']}" "$APP"
grep -Fq "allowed={['ADMIN']}" "$APP"
grep -Fq "'/api/customer/marketplace/shops'" "$CUSTOMER"
grep -Fq "'/api/customer/marketplace/categories'" "$CUSTOMER"
grep -Fq 'buildMarketplaceSearchPath' "$CUSTOMER"
grep -Fq 'CustomerDiscoverySearch' "$CUSTOMER"
grep -Fq 'searchMarketplaceShops' "frontend/src/features/customer/CustomerDiscoverySearch.tsx"
grep -Fq '/api/customer/cart/' "$CART"
grep -Fq '/checkout' "$CART"
grep -Fq '/api/payments/orders/' "$CART"
grep -Fq '/api/payments/verify' "$CART"
grep -Fq '/api/customer/orders' "$ORDERS"
grep -Fq '/api/orders/' "$ORDERS"
grep -Fq 'cancel' "$ORDERS"

# Shopkeeper order, stock and analytics work uses authenticated backend routes.
grep -Fq "'/api/shopkeeper/shops'" "$SHOPKEEPER"
grep -Fq "'/api/shopkeeper/orders'" "$SHOPKEEPER"
grep -Fq '/inventory' "$SHOPKEEPER"
grep -Fq '/restock' "$SHOPKEEPER"
grep -Fq '/movements' "$SHOPKEEPER"
grep -Fq '/fulfilment?' "$SHOPKEEPER"
grep -Fq '/analytics/overview?' "$SHOPKEEPER"
grep -Fq '/analytics/daily?' "$SHOPKEEPER"
grep -Fq '/analytics/top-products?' "$SHOPKEEPER"
grep -Fq '/api/shopkeeper/ai-insights/replenishment?' "$SHOPKEEPER"
grep -Fiq 'weighted moving-average baseline' "$SHOPKEEPER"
grep -Fq "['analytics', 'Performance'" "$SHOPKEEPER"
grep -Fq 'shopkeeper-bottom-nav' "$SHOPKEEPER"

# Delivery partners and administrators have role-specific live operations.
grep -Fq '/api/delivery/orders/available' "$DELIVERY"
grep -Fq '/api/delivery/orders' "$DELIVERY"
grep -Fq "action: 'accept' | 'pickup' | 'deliver'" "$DELIVERY"
grep -Fq "'/api/delivery/orders/' + order.id + '/' + action" "$DELIVERY"
grep -Fq 'operations-bottom-nav' "$DELIVERY"
grep -Fq '/api/admin/analytics/overview' "$ADMIN"
grep -Fq '/api/admin/users' "$ADMIN"
grep -Fq '/api/admin/shops' "$ADMIN"
grep -Fq 'CatalogueReviewQueue' "$ADMIN"
# Phase 4 makes the entire product catalogue text-only, including administration.
if grep -Eiq 'ProductPhotoReviewQueue|ProductPhoto|ApprovedProductImage|FallbackImage|imageUrl|imageSourceName|approvedImageUrl|candidateImageUrl|<img[[:space:]>]' "$ADMIN" "$CUSTOMER" "$SHOPKEEPER"; then
  echo "Product catalogue interfaces must remain text-only; product-photo UI is not supported." >&2
  exit 1
fi
grep -Fq 'operations-bottom-nav' "$ADMIN"

# Notifications are loaded and mutated through the authenticated per-user API.
NOTIFICATIONS="frontend/src/features/shared/NotificationCenter.tsx"
grep -Fq "'/api/notifications/unread-count'" "$NOTIFICATIONS"
grep -Fq "'/api/notifications?page=0&size=20'" "$NOTIFICATIONS"
grep -Fq "'/api/notifications/read-all'" "$NOTIFICATIONS"
grep -Fq 'NotificationCenter' "frontend/src/features/customer/CustomerShell.tsx" "$SHOPKEEPER" "$DELIVERY" "$ADMIN"
grep -Fq 'notificationDestination' "frontend/src/features/shared/notifications.test.ts"

# Motion must be small, CSS-only and disabled for reduced-motion users.
grep -Fq '@keyframes apniko-soft-rise' "$GLOBAL"
grep -Fq '@keyframes apniko-bar-rise' "$GLOBAL"
grep -Fq 'prefers-reduced-motion: reduce' "$GLOBAL"

if grep -Eiq 'demo|mockData|fakeProducts|sampleProducts' "$CUSTOMER" "$SHOPKEEPER" "$DELIVERY" "$ADMIN"; then
  echo "Role workspaces must use live API data, not hardcoded demo data." >&2
  exit 1
fi

if grep -Eiq 'Open Food Facts|open food facts' "$ADMIN"; then
  echo "Excluded catalogue source must not be offered in administration." >&2
  exit 1
fi

echo "Phase 6 verification passed: live customer checkout/orders, shopkeeper orders/inventory/analytics, delivery lifecycle, admin operations, responsive navigation and reduced-motion-aware UI are present."
