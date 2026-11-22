# Apniko — Phase 0 Foundation Audit

**Audit date:** 2026-11-22  
**Working branch:** `chore/phase-0-foundation-cleanup`  
**Foundation PR:** [#56 — draft, not merged to main](https://github.com/Akprasad614/Apniko/pull/56)  
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
- BigBasket is excluded. No external dataset is currently approved or
  imported.
- The Indian Packaged Foods Nutritional Composition Dataset (2026) on
  Mendeley Data is a proposed Phase 2 packaged-food seed only. It must not be
  imported until the actual CSV/README, field semantics, provenance and
  licence scope have been inspected and a tested importer is approved.
- Do not invent GTINs, barcodes, pack sizes, prices, ratings, stock levels or
  product photographs.
- A nutritional serving size is not automatically a retail pack size.
- Dataset prices must not be shown as current shop prices or verified MRP.
- Preserve the distinction between shared master product identity and
  shop-specific inventory, price and stock.

## Safety boundaries

The owner-reported backup path is:

`~/Desktop/apniko-catalogue-reset-20261122-165627/localcart-before-reset.sql`

The reset was performed on 2026-11-22 after the backend was stopped. The
owner supplied the reset confirmation and reported the final counters as
zero. This repository audit does not independently connect to the owner's
Mac, local MySQL instance or Desktop backup file. Do not rerun the
destructive reset to check this.

No production database, remote data, credentials or customer records were
changed. No deployment was made. The protected `main` branch remains at
`3f8db823b4cc5a8fb49104d94cadfed72d29544e` in the recorded repository
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
checkout, order history/status and cancellation flows; and the owner-approved
Color Pop text-first presentation. Customer marketplace shop responses omit
private shop-owner names and phone numbers. The user visually reviewed and
approved the storefront appearance.

## Automated validation

The recorded working-branch head `6202c0fdc498135789ac1ecabd27e3cc4ee1a146`
had 22 check-run records. Successful runs covered frontend lint/typecheck/
tests/build, backend Maven and Phase 10 verification, CodeQL, Semgrep,
Gitleaks, direct OSV dependency scanning, CycloneDX SBOM/dependency
submission and passive DAST.

Three duplicate/restarted check-run records were cancelled while equivalent
successful runs existed for the same commit. The OSV code-scanning integration
reported **neutral** because the older scheduled-workflow configuration was
not present on that PR branch; the separate dependency-scan workflow passed.
This is not recorded as an OSV code-scanning success.

The documentation-only closeout commit that adds this audit update must also
receive its own successful applicable CI/security checks before PR #56 is
made ready for review.

## Phase 0 exit criteria

- [x] Existing Java/Spring Boot + MySQL and React/TypeScript/Vite
      architecture recorded without migration.
- [x] Cleanly named working branch created from the unmerged catalogue work.
- [x] Foundation documentation, ownership metadata and CI branch coverage
      aligned with the Apniko repository.
- [x] GitHub tracked-tree scan at the recorded head: 339 tracked files,
      no tracked image files and no Open Food Facts-named paths.
- [x] Automated frontend, backend, catalogue safety and security checks have
      successful results on the recorded head, with the OSV neutral result
      and cancelled duplicates disclosed above.
- [x] Phase 1 merged into the Phase 0 working branch and visually accepted.
- [x] Owner-reported backup-first local reset completed with all nine
      reported verification counters at zero.
- [ ] On the owner's Mac, verify the existing SQL backup is present and
      non-empty; do not rerun the reset.
- [ ] On the owner's Mac, inspect ignored and untracked files for old product
      photos, Open Food Facts files, downloaded private data or credentials.
- [ ] Confirm no local working copy still depends on the superseded branch
      `feature/apniko-dyad-frontend`. Retire that remote ref only after the
      owner confirms; the current GitHub connector cannot delete branch refs.

**Phase status:** Phase 1 is complete. Phase 0 implementation and remote
verification are complete, but Phase 0 is **not yet formally closed** until
the three owner-side closeout items above are verified. PR #56 remains a
draft. No merge to `main`, production database operation or deployment is
authorised or claimed by this audit.
