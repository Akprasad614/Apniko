#!/usr/bin/env bash
set -euo pipefail

# Node 22 runners bundle npm 10, whose Arborist can crash on optional peer
# dependency graphs. npm 11.21.2 includes the resolver guard for that issue.
# --no-package-lock keeps the production frontend lockfile untouched.
npx --yes npm@11.21.2 install --no-save --no-package-lock --no-audit --no-fund \
  @playwright/test@1.65.1 \
  @axe-core/playwright@4.14.0
