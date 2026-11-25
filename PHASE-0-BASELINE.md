# Apniko — Phase 0 Foundation Audit

**Audit date:** 2026-11-22  
**Working branch:** `chore/phase-0-foundation-cleanup`  
**Foundation PR:** [#56 — not merged to main](https://github.com/Akprasad614/Apniko/pull/56)  
**Phase 1 PR:** [#57 — merged into the Phase 0 working branch](https://github.com/Akprasad614/Apniko/pull/57)  
**Scope:** Repository architecture, development conventions, clean catalogue
starting conditions, automation entry points and phase completion evidence.

## Verified architecture

| Area | Current repository baseline |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Jakarta Validation |
| Database | MySQL via mysql-connector-j |
| API documentation | Springdoc OpenAPI and Swagger UI |
| Frontend | React, TypeScript, Vite |
| Routing | React Router |
| Frontend checks | ESLint, TypeScript build/typecheck, Vitest |
| Backend checks | Maven verify, JUnit, JaCoCo |
| API testing | Postman collections |
| CI | GitHub Actions, Java 21 and MySQL service for backend verification |

The existing repository is a Spring Boot + MySQL backend with a separate
React/TypeScript frontend. Phase 0 does not replace the backend, migrate the
database, or rebuild the frontend.

## Repository conventions

- Backend domain code remains under `src/main/java/com/localcart/`.
- Frontend route composition, authentication, domain features, shared
  components and API utilities remain in their existing feature directories.
- Frontend commands are `npm run lint`, `npm run typecheck`,
  `npm run test:ci` and `npm run build`.
- Backend verification uses the committed Maven wrapper.
- Secrets and local environment files must not be committed.
- Changes are developed on named feature/chore branches and reviewed through
  pull requests; `main` is not edited directly.

## Phase 0 cleanup applied

1. Created the clean development branch
   `chore/phase-0-foundation-cleanup` from the existing unmerged catalogue
   work, preserving the implementation and its history.
2. Removed obsolete tooling-specific metadata and ignore entries identified
   during the foundation cleanup.
3. Corrected the repository ownership record to point to Apniko.
4. Updated root documentation with the verified stack, project layout and
   clean catalogue policy.
5. Updated backend and frontend CI branch filters so the new chore branch
   receives verification on pushes and pull requests.
6. Added this baseline record so later phases have a stable architecture and
   acceptance boundary.
7. Opened draft PR #56. PR #55 was closed as superseded; none of its changes
   were merged.

## Catalogue and image guardrails

- The local development catalogue reset was run by the project owner with a
  full backup first. The owner reported zero products, product photos, photo
  history, catalogue source records, product categories, shop inventory, cart
  items, order items and orders.
- Open Food Facts and related catalogues are excluded.
- Phase 2 selected only the Indian Packaged Foods Nutritional Composition
  Dataset (2026) on Mendeley Data, Version 1, DOI
  `10.17632/rhswn9z9xr.1`, identified as CC BY 4.0.
- The supplied CSV/README, field semantics, provenance and licence terms
  were inspected; a Mendeley-only importer and dry-run were completed.
- The dataset contains no verified retail GTINs or retail pack sizes. Phase 3
  preserves its source provenance and holds incomplete rows for review; Phase 4
  removes product-photo and image-related features from every catalogue surface.
- Do not invent GTINs, barcodes, pack sizes, prices, ratings, stock levels or
  product photographs.
- A nutritional serving size is not automatically a retail pack size.
- Dataset prices must not be shown as current shop prices or verified MRP.
- Preserve the distinction between shared master product identity and
  shop-specific inventory, price and stock.
- No retailer catalogue, retailer scraping or retailer-owned imagery is an
  approved source path. The Phase 3 importer accepts only the exact inspected
  Mendeley dataset contract and keeps every row in review; Phase 4 excludes all
  product-image sources, storage, uploads, moderation and rendering.

## Safety boundaries

A full local SQL backup was created and verified before the development
catalogue reset on 2026-11-22. The owner recorded zero products, product
photos, photo history, source records, categories, shop inventory, cart items,
order items and orders after the reset. This is a historical record of the
local development database only; it is not evidence about a staging or
production database. Do not repeat the reset to re-check this record.

The owner also ran a working-tree inspection. `git status --short --ignored`
showed only ignored generated/development directories:
`.venv-catalogue/`, `frontend/.venv-catalogue/`, `frontend/dist/`,
`frontend/node_modules/`, `scripts/catalogue/__pycache__/` and
`target/`. The image/catalogue-feed filename scan returned no matches
outside the explicitly pruned Git, dependency, build-output and virtualenv
directories. The GitHub tracked-tree scan at the recorded code head found
339 tracked files and no tracked image files. This is a repository/worktree
scan, not a claim about unrelated files elsewhere on the Mac.

`git worktree list` showed only the current checkout, on
`feature/phase-2-authorized-catalogue-intake`. The legacy branch
`feature/apniko-dyad-frontend` remains as a local and remote branch
reference, but is not checked out in the current clone. It is being retained
rather than deleted because other separate clones cannot be inspected from
this checkout. This branch-retirement housekeeping is not a Phase 0
implementation blocker.

No production database, remote data, credentials or customer records were
changed. No deployment was made. The protected `main` branch remained at
`3f8db823b4cc5a8fb49104d94cadfed72d29544e` in the verified repository
snapshot.

## Phase 1 completion

Phase 1 customer shopping and order experience is complete. PR #57 was
merged on 2026-11-22 into
`chore/phase-0-foundation-cleanup` at merge commit
`6202c0fdc498135789ac1ecabd27e3cc4ee1a146`; it was not merged into
`main`.

The merged work includes the customer Home, Categories, Cart, Orders,
Account, Addresses and Payment Methods routes; five-destination responsive
navigation; shop/product discovery and shop-scoped browsing; basket,
checkout, order history/status and cancellation flows; and the Color Pop
text-first presentation. Customer marketplace shop responses omit private
shop-owner names and phone numbers.

## Automated validation

The documentation closeout commit
`d636ddd5c09a2d63b0fd922016a4e5d8a4db26fa` completed all 16 check-run
records: 15 succeeded, 1 was neutral and none failed. Successful runs covered
frontend lint/typecheck/tests/build, backend and Phase 10 verification,
CodeQL, Semgrep, Gitleaks, direct OSV dependency scanning, CycloneDX
dependency inventory/submission and passive DAST.

The OSV code-scanning integration reported **neutral** because the older
scheduled-workflow configuration was not present on the PR branch; the
separate dependency-scan workflow passed. This is not recorded as an OSV
code-scanning success.

## Phase 0 exit criteria

- [x] Existing Java/Spring Boot + MySQL and React/TypeScript/Vite
      architecture recorded without migration.
- [x] Cleanly named working branch created from the unmerged catalogue work.
- [x] Foundation documentation, ownership metadata and CI branch coverage
      aligned with the Apniko repository.
- [x] GitHub tracked-tree inspection: 339 tracked files, no tracked image
      files and no Open Food Facts-named paths.
- [x] Owner's local SQL backup verified present and non-empty.
- [x] Owner's working-tree inspection found no matching product image or
      Open Food Facts paths in the scanned project files.
- [x] Owner-reported backup-first reset completed with all nine reported
      verification counters at zero.
- [x] Automated frontend, backend, catalogue safety and security checks
      completed on the documentation closeout head: 15 success, 1 neutral,
      0 failures; neutral OSV integration disclosed above.
- [x] Phase 1 merged into the Phase 0 working branch and visually accepted.
- [x] Only the Phase 2 checkout appears in the current clone's worktree list;
      the old branch reference is preserved, not checked out or deleted.

**Phase status: COMPLETE.** Phase 0 foundation and local clean-catalogue
starting conditions are complete. Phase 1 customer experience is complete.
The separate legacy branch reference is retained as non-blocking repository
housekeeping. No merge to `main`, production database operation, catalogue
import or deployment is claimed or authorised by this audit.
