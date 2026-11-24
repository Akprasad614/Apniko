# Apniko

Apniko is a neighbourhood-commerce platform being built to help independent
grocery shops serve nearby customers through a shared digital storefront.
Shopkeepers manage their catalogue, inventory, and incoming orders; customers
discover local shops and order everyday essentials.

## Technology

- Backend: Java, Spring Boot, Spring Security, JPA, MySQL
- Frontend: React, TypeScript, Vite
- API documentation and testing: OpenAPI / Swagger UI, Postman

## Repository layout

- `src/` — Spring Boot backend
- `frontend/` — React customer application
- `postman/` — API and end-to-end test collections
- `scripts/` — local verification and isolated test helpers

## Local development

See `PHASE10-README.md` for the backend, Swagger, and isolated database
workflow. See `frontend/README.md` for frontend setup and checks.

## Project rights

See `COPYRIGHT.md` and `OWNERSHIP.md`. Third-party dependencies and assets
remain subject to their own licences.

## Production readiness and operations

Phase 8 deployment preparation, secure browser authentication and release gates are documented in:

- [Phase 8 production readiness](PHASE8-PRODUCTION-READINESS.md)
- [Operations runbook](OPERATIONS-RUNBOOK.md)
- [Main branch protection setup](MAIN-BRANCH-PROTECTION.md)
- [Production environment example](.env.production.example)

The Compose stack is designed for a managed MySQL database and a host with a public DNS name. Do not use it against an empty production database until a reviewed schema migration baseline has been created. Real production secrets, hosting, DNS, GitHub ruleset activation, monitoring and offsite backup configuration remain owner-controlled release gates.
