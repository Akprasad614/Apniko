#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

required=(
  "src/main/java/com/localcart/controller/ProductCatalogImportController.java"
  "src/main/java/com/localcart/service/ProductCatalogImportService.java"
  "src/main/java/com/localcart/controller/ProductCatalogueModerationController.java"
  "src/main/java/com/localcart/service/ProductCatalogueModerationService.java"
  "src/main/java/com/localcart/controller/ProductCategoryController.java"
  "src/main/java/com/localcart/service/ProductCategoryService.java"
  "src/main/java/com/localcart/dto/ProductImportBatchRequest.java"
  "src/main/java/com/localcart/dto/ProductImportBatchResponse.java"
  "frontend/src/features/admin/AdminDashboard.tsx"
  "frontend/src/features/admin/CatalogueReviewQueue.tsx"
  "frontend/src/features/admin/catalogueReview.test.ts"
  "src/test/java/com/localcart/service/ProductCatalogImportServiceTest.java"
  "src/test/java/com/localcart/service/ProductCatalogueModerationServiceTest.java"
  "src/test/java/com/localcart/service/ProductCategoryServiceTest.java"
  "scripts/catalogue/import_catalogue.py"
  "PHASE5-ADMIN-CATALOGUE-COMPLETION.md"
)
for file in "${required[@]}"; do
  [[ -s "$file" ]] || { echo "Missing Phase 5 admin catalogue file: $file" >&2; exit 1; }
done

grep -Fq '@PreAuthorize' src/main/java/com/localcart/controller/ProductCatalogImportController.java
grep -Fq "hasRole('ADMIN')" src/main/java/com/localcart/controller/ProductCatalogImportController.java
grep -Fq 'products().size() > 500' src/main/java/com/localcart/service/ProductCatalogImportService.java
grep -Fq 'MAX_BATCH_SIZE = 250' scripts/catalogue/import_catalogue.py
grep -Fq 'if not args.apply:' scripts/catalogue/import_catalogue.py
grep -Fq 'args.license_confirmed' scripts/catalogue/import_catalogue.py
grep -Fq 'args.commercial_use_confirmed' scripts/catalogue/import_catalogue.py
grep -Fq 'NEEDS_REVIEW' src/main/java/com/localcart/service/ProductCatalogImportService.java
grep -Fq 'getReviewQueue' src/main/java/com/localcart/controller/ProductCatalogueModerationController.java
grep -Fq "@PreAuthorize(\"hasRole('ADMIN')\")" src/main/java/com/localcart/controller/ProductCategoryController.java
grep -Fq 'async function createCategory' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'categoryNameExists' frontend/src/features/admin/catalogueReview.ts
grep -Fq 'CATALOGUE TAXONOMY' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'CONTROLLED BULK INTAKE' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'operations-bottom-nav--five' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq '/api/admin/catalogue/review-queue' PHASE5-ADMIN-CATALOGUE-COMPLETION.md
grep -Fq 'async function createCategory' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq "'/api/catalogue/categories'" frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'CATALOGUE TAXONOMY' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'CONTROLLED BULK INTAKE' frontend/src/features/admin/AdminDashboard.tsx
grep -Fq 'operations-bottom-nav--five' frontend/src/features/admin/AdminDashboard.tsx

echo "Phase 5 admin catalogue verification passed: admin-only bounded import, dry-run-first CSV intake, source checks and moderation workflow are present."
