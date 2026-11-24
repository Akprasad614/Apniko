#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

forbidden_paths=(
  "frontend/src/components/FallbackImage.tsx"
  "frontend/src/components/ProductPhotoCredit.tsx"
  "frontend/src/features/admin/ProductPhotoReviewQueue.tsx"
  "frontend/src/features/customer/ApprovedProductImage.tsx"
  "src/main/java/com/localcart/controller/ProductPhotoAdminController.java"
  "src/main/java/com/localcart/entity/ProductPhoto.java"
  "src/main/java/com/localcart/service/ProductPhotoService.java"
  "scripts/catalogue/import_authorized_product_photos.py"
  "PRODUCT-PHOTOS.md"
  "PHASE-3-PRODUCT-PHOTOGRAPHY.md"
)

for path in "${forbidden_paths[@]}"; do
  if [[ -e "$path" ]]; then
    echo "Product-image code or documentation must not be present: $path" >&2
    exit 1
  fi
done

if grep -RInE 'ProductPhoto|ProductImage|ProductImageReviewStatus|ProductPhotoCredit|ApprovedProductImage|imageUrl|imageSourceName|approvedImageUrl|candidateImageUrl|photoUrl|\\.photo\\b|getPhoto\\(|setPhoto\\(|<img[[:space:]>]' \
  src/main/java src/test/java frontend/src scripts/catalogue; then
  echo "Product-image references remain in application code." >&2
  exit 1
fi

test -s scripts/catalogue/remove-legacy-product-photo-tables.sql
grep -Fq 'A verified GTIN and retail pack size are required before publishing' src/main/java/com/localcart/service/ProductCatalogueModerationService.java
grep -Fq 'A verified GTIN and retail pack size are required' src/main/java/com/localcart/service/ProductCatalogueModerationService.java

echo "Phase 4 text-only catalogue verification passed."
