#!/usr/bin/env bash
set -euo pipefail
npm install --no-save --no-package-lock --no-audit --no-fund \
  @playwright/test@1.65.1 \
  @axe-core/playwright@4.14.0
