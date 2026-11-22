# Apniko — Phase 0 Foundation Audit

**Audit date:** 2026-11-22  
**Current branch:** `chore/phase-0-foundation-cleanup`  
**Current review:** Draft PR #56  
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
   work, preserving the implementation and its history.
2. Removed the obsolete tooling-specific comment from
   `frontend/.gitattributes`; retained the useful LF normalization rule.
3. Removed the obsolete hidden-tool directory ignore entry from
   `frontend/.gitignore` and deleted the now-empty file.
4. Corrected the ownership record to point to the current Apniko repository.
5. Updated the root README with the verified stack, project layout and
   catalogue-source guardrails.
6. Updated backend and frontend CI branch filters so the new chore branch
   receives verification on pushes and pull requests.
7. Added this baseline record so later phases have a stable architecture and
   acceptance boundary.
8. Opened draft PR #56. The previous catalogue-reset PR #55 was closed as
   superseded; none of its changes were merged.

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

- The local catalogue reset helper was reviewed and run by the project owner
  on 2026-11-22 after the backend was stopped. The operator supplied the exact
  confirmation phrase. The script created a full SQL backup under
  `~/Desktop/apniko-catalogue-reset-20261122-165627/` before executing the
  reset. The owner reported the final verification counts as zero for
  products, product photos, photo history, catalogue source records, product
  categories, shop inventory, cart items, order items and orders.
- The local reset is confirmed from the operator's terminal output; this
  repository audit does not independently connect to or inspect the local
  MySQL instance or backup file.
- No production database, remote data, credentials or customer records were
  changed.
- No pull request was merged and no deployment was made.
- Existing catalogue work from the prior unmerged pull request is preserved
  on the new clean branch.
- The old remote branch reference remains temporarily. The available GitHub
  integration can create branches but cannot delete remote branch refs.
  Current project files and the PR #56 diff have been scanned and contain no
  obsolete tool-specific references. Historical PR/commit metadata has not
  been rewritten.

## Validation status

The latest PR #56 head at this audit update is
`94b652daba9b1228e9a97aff1af6a62f49c94553`. GitHub reported 13 check runs
against this exact commit. The frontend lint/typecheck/Vitest/build workflow,
backend Maven and catalogue safety verification, Gitleaks, Semgrep, CodeQL,
passive DAST, dependency scan and Maven dependency inventory completed
successfully. CycloneDX SBOM generation also completed successfully.

The separate OSV dependency-scan workflow passed. GitHub's OSV code-scanning
integration reported **neutral**, not failed: it could not find the older
scheduled-workflow configuration on the PR branch, so it could not compare
alerts for that configuration. This is recorded as a configuration warning,
not as a successful code-scanning result.

These checks are against the current PR head, not merely an earlier commit.
No production deployment or database operation was part of these workflows.

## Phase 0 exit criteria

- [x] Existing Java/Spring Boot + MySQL and React/TypeScript/Vite architecture
      recorded without migration.
- [x] Cleanly named working branch created from the unmerged catalogue work.
- [x] Obsolete tool-specific references removed from the current project files
      identified in the PR diff.
- [x] Root documentation and ownership metadata aligned with the current
      Apniko repository.
- [x] Backend and frontend CI configured for the new branch and PR.
- [x] Frontend CI, backend CI, catalogue reset safety tests, Gitleaks,
      Semgrep, dependency scan and SBOM generation passed on the current PR
      head.
- [x] CodeQL and passive DAST completed successfully on the current PR head.
- [x] Owner-reported local backup-first catalogue reset completed; all nine
      reported verification counters are zero.
- [ ] Run a local working-tree scan, including ignored and untracked files.
- [ ] Delete the legacy remote branch after the owner confirms the local scan
      is clean and no other working copy still depends on that branch.

Phase 0 implementation, automated verification and the local catalogue reset
are complete. The remaining closure items are the local ignored/untracked
filesystem scan and legacy branch retirement. BigBasket import remains blocked
until written reuse permission is verified. No branch deletion, merge or
deployment is claimed.
