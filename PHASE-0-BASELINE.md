# Apniko — Phase 0 Foundation Audit

**Audit date:** 2026-11-22  
**Scope:** Repository architecture, development conventions, catalogue policy,
automation entry points and safe starting conditions.

## Verified architecture

| Area | Current repository baseline |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Jakarta Validation |
| Database | MySQL via mysql-connector-j |
| API documentation | Springdoc OpenAPI 3.2.0 and Swagger UI |
| Frontend | React 19.3.1, TypeScript 5.9.3, Vite 8.3.8 |
| Routing | React Router 8.5.1 |
| Frontend checks | ESLint, TypeScript build/typecheck, Vitest |
| Backend checks | Maven verify, JUnit, JaCoCo |
| API testing | Postman collections |
| CI | GitHub Actions, Java 21 and MySQL 8.4 service for backend verification |

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
   work, preserving that work without rewriting its commits.
2. Removed the obsolete tooling-specific comment from
   `frontend/.gitattributes`; retained the useful LF normalization rule.
3. Removed the obsolete hidden-tool directory ignore entry from
   `frontend/.gitignore` and deleted the now-empty file.
4. Corrected the ownership record to point to the current Apniko repository.
5. Updated the root README with the verified stack, project layout and
   catalogue-source guardrails.
6. Updated backend CI branch filters so `chore/**` branches receive push and
   pull-request verification.
7. Added this baseline record so later phases have a stable architecture and
   acceptance boundary.

## Catalogue and image guardrails

- Start with zero products, product photos, photo candidates, source records
  and seeded product categories in a freshly reset local development
  catalogue.
- Open Food Facts and its related catalogue services are excluded.
- BigBasket is only a planned source. A public Kaggle copy is not proof of
  commercial reuse rights; no import may be applied until written permission
  for Apniko's intended use is verified.
- Do not invent GTINs, pack sizes, prices, ratings, stock levels or product
  photographs.
- Do not scrape retailer pages or use retailer images without express written
  permission.
- Preserve the distinction between shared master product identity and
  shop-specific inventory, price and stock.

## Safety boundaries

- The local catalogue reset helper is a separate, destructive operation. It
  must be reviewed and run by the project owner on the local machine after
  stopping the backend. Phase 0 did not run it and did not access the local
  MySQL instance.
- No production database, remote data, credentials or customer records were
  changed.
- No pull request was merged and no deployment was made.
- Existing catalogue work from the prior unmerged pull request is preserved
  on the new clean branch.

## Validation status

Repository content and configuration were inspected through GitHub. The
following automated checks still need a successful GitHub Actions run against
this branch before Phase 0 can be called fully verified:

- Backend workflow: catalogue scripts, Phase 10 assets, Maven verification
  and Java tests.
- Frontend workflow: lint, TypeScript, unit tests and production build.
- Security workflows: confirm the current branch/PR is included in their
  configured triggers and inspect the latest results.

A workflow configuration or a previously passing run is not evidence that the
current branch has passed. Report each check as passed, failed or not run
using the actual run result.

## Phase 0 exit criteria

- [x] Existing Java/Spring Boot + MySQL and React/TypeScript/Vite architecture
      recorded without migration.
- [x] A cleanly named working branch created from the unmerged catalogue work.
- [x] Obsolete tool-specific repository references removed from the files
      identified in the current pull-request diff.
- [x] Root documentation and ownership metadata aligned with the current
      Apniko repository.
- [x] Backend CI configured to run on the new chore branch pattern.
- [ ] GitHub Actions verification passes on this branch.
- [ ] Local owner-run catalogue backup/reset is reviewed and completed.
- [ ] Full repository and local working-tree scan confirms no remaining
      obsolete tool directory or references outside the inspected GitHub
      content.

Phase 0 is implementation-complete but remains verification-pending until the
unchecked items are completed. The local reset and local filesystem scan
require the project owner's Mac; they are intentionally not simulated or
claimed as complete here.
