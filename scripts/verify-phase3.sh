#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PHASE2_VENV="${TMPDIR:-/tmp}/apniko-phase2-venv"
PYTHON="$PHASE2_VENV/bin/python"
if [[ ! -x "$PYTHON" ]]; then
  echo "Catalogue test environment is missing. Run scripts/verify-phase2.sh first." >&2
  exit 1
fi

(
  cd scripts/catalogue
  "$PYTHON" -m unittest -v test_import_catalogue
)
"$PYTHON" -m py_compile scripts/catalogue/import_catalogue.py

grep -Fq 'APPROVED_SOURCE_SHA256' scripts/catalogue/import_catalogue.py
grep -Fq 'Reference dataset row only' scripts/catalogue/import_catalogue.py
grep -Fq '"review_required_rows": review_rows' scripts/catalogue/import_catalogue.py

echo "Phase 3 catalogue intake verification passed. All Mendeley rows remain review candidates."
