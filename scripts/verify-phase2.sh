#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PHASE2_VENV="${TMPDIR:-/tmp}/apniko-phase2-venv"
python3 -m venv "$PHASE2_VENV"
"$PHASE2_VENV/bin/python" -m pip install --disable-pip-version-check --quiet -r scripts/catalogue/requirements.txt
"$PHASE2_VENV/bin/python" -m unittest discover -s scripts/catalogue -p 'test_*.py' -v
"$PHASE2_VENV/bin/python" -m py_compile scripts/catalogue/import_catalogue.py
