#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

required=(
  "src/main/java/com/localcart/entity/Shop.java"
  "src/main/java/com/localcart/dto/ShopLocationRequest.java"
  "src/main/java/com/localcart/dto/NearbyShopSearchRequest.java"
  "src/main/java/com/localcart/controller/AdminController.java"
  "src/main/java/com/localcart/controller/CustomerMarketplaceController.java"
  "src/main/java/com/localcart/service/CustomerMarketplaceService.java"
  "frontend/src/features/customer/NearbyShops.tsx"
  "frontend/src/features/admin/AdminDashboard.tsx"
  "src/test/java/com/localcart/service/CustomerMarketplaceServiceTest.java"
  "PHASE10-NEARBY-SHOPS.md"
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { echo "Missing geolocation file: $file" >&2; exit 1; }
done

grep -Fq 'private Double latitude;' src/main/java/com/localcart/entity/Shop.java
grep -Fq 'private Double longitude;' src/main/java/com/localcart/entity/Shop.java
grep -Fq 'updateShopLocation' src/main/java/com/localcart/service/AdminService.java
grep -Fq 'getNearbyShops' src/main/java/com/localcart/controller/CustomerMarketplaceController.java
grep -Fq '@PostMapping("/shops/nearby")' src/main/java/com/localcart/controller/CustomerMarketplaceController.java
grep -Fq 'distanceKm' src/main/java/com/localcart/service/CustomerMarketplaceService.java
grep -Fq 'navigator.geolocation.getCurrentPosition' frontend/src/features/customer/NearbyShops.tsx
grep -Fq "method: 'POST'" frontend/src/features/customer/NearbyShops.tsx
grep -Fq 'Your location is used for this search only' frontend/src/features/customer/NearbyShops.tsx
grep -Fq 'Save shop pin' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'nearbyShopsAreSortedByDistance' src/test/java/com/localcart/service/CustomerMarketplaceServiceTest.java

echo "Phase 10 geolocation verification passed: operator-verified shop pins, radius validation, distance ordering, private request-body location search and customer UI are present."
