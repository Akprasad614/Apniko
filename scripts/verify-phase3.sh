#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PHASE2_VENV="${TMPDIR:-/tmp}/apniko-phase2-venv"
PYTHON="$PHASE2_VENV/bin/python"
if [[ ! -x "$PYTHON" ]]; then
  echo "Phase 2 test environment is missing. Run scripts/verify-phase2.sh first." >&2
  exit 1
fi

"$PYTHON" -m py_compile scripts/catalogue/import_product_photos.py scripts/catalogue/import_authorized_product_photos.py scripts/catalogue/refresh_product_photos.py scripts/catalogue/refresh_all_product_photos.py
(
  cd scripts/catalogue
  "$PYTHON" -m unittest -v test_import_product_photos test_import_authorized_product_photos
)

echo "Phase 3 authentic product photo tests passed."
