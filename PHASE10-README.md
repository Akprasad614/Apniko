# Apniko Phase 10 — API documentation and isolated E2E testing

Phase 10 adds API documentation, accurate JWT documentation, a repeatable Postman workflow, and a separate local test database. It does not alter the main `localcart` database or any customer-facing business logic.

## What is included

- Swagger UI and OpenAPI 3 through Springdoc.
- Swagger UI/API-doc routes permitted by Spring Security.
- Operation-level JWT documentation: protected operations require `bearerAuth`; login, registration, `/api/test`, and the Razorpay webhook are documented as public.
- A normal API Postman collection for requests against port `8080`.
- An end-to-end Postman collection that is safety-locked to `localhost:8081` or `127.0.0.1:8081`.
- Scripts to clone the local database into `localcart_test` without overwriting an existing non-empty test database, and to start the app against that test database.
- E2E checks for the immediate cart POST quantity, the subsequent GET quantity, checkout order contents, and cart clearing.

## 1. Verify the Phase 10 files

From the backend project root:

```bash
bash scripts/verify-phase10.sh
```

This checks XML/JSON syntax, shell syntax, OpenAPI security annotations in the checked-in spec, and the E2E assertions. It does not need MySQL or start the application.

## 2. Open Swagger UI against your normal local app

Start the backend using your existing local environment variables:

```bash
./mvnw spring-boot:run
```

Then open:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

The Swagger **Authorize** dialog accepts the JWT token value only. Do not type the `Bearer ` prefix; Swagger adds it.

## 3. Prepare the isolated E2E database

The E2E collection must not run against the main application on port `8080`. It is deliberately restricted to port `8081`.

First, clone the source database once:

```bash
bash scripts/clone-localcart-test-db.sh
```

The script reads the local MySQL root password securely. It creates `localcart_test` only if needed and refuses to populate it if it already contains tables. It never drops the database and never writes to `localcart`.

## 4. Start the isolated app

In a separate Terminal window, from the backend project root:

```bash
bash scripts/run-localcart-test-instance.sh
```

The script asks for `DB_PASSWORD` and `JWT_SECRET` if they are not already set, verifies that `localcart_test` exists, then starts Spring Boot on port `8081` using that database.

Leave this terminal running while Postman executes the collection.

## 5. Configure Postman once

For the regular API collection on port `8080`:

1. Import `postman/collections/localcart-api.postman_collection.json`.
2. Import `postman/environments/localcart-api.local.postman_environment.example.json`.
3. Duplicate the example environment and name the private copy `Apniko API — PRIVATE`.
4. Enter the customer password in that private environment. The login request saves the returned JWT into the private `bearerToken` variable, which is used by the remaining protected requests.
5. Keep `baseUrl` as `http://localhost:8080`.

For the isolated E2E collection on port `8081`:

1. Import `postman/collections/Apniko-E2E.postman_collection.json`.
2. Import `postman/environments/localcart.local.postman_environment.example.json`.
3. Duplicate the example environment and name the private copy `Apniko Local E2E — PRIVATE`.
4. Enter the passwords for the preconfigured customer, shopkeeper, and delivery partner test accounts in the private environment only. The example file intentionally contains blank password and token values.
5. Keep `baseUrl` as `http://localhost:8081`.
6. Select the private environment and run the E2E collection in order.

The collection checks that:

- the selected shop is active and the configured product is available;
- the quantity before adding is recorded;
- the immediate POST response contains the product with `quantityBefore + quantityAdded`;
- the GET cart response has the same quantity;
- checkout creates a `PLACED` platform-delivery order containing the expected item and quantity;
- successful checkout clears the cart;
- the shopkeeper moves the order through `ACCEPTED`, `PREPARING`, and `READY`;
- the delivery partner accepts, picks up, and delivers the order;
- the assigned delivery partner records COD collection; and
- the customer can retrieve the delivered order, its status history, and the paid COD payment.

## 6. Re-running the full E2E workflow

Checkout deducts inventory in `localcart_test`. The clone script intentionally refuses to overwrite a non-empty test database. Before a fresh full run, back up any test data you need and refresh **only** `localcart_test`; do not drop or modify the main `localcart` database. The collection cannot target port `8080` by design.

## 7. Git safety

The private Postman environment and generated Postman reports are ignored by `.gitignore`. Commit the example environment, collections, OpenAPI snapshot, scripts, and Phase 10 source configuration. Keep `pom-phase10-backup.xml` and `SecurityConfig-phase10-backup.java` as local comparison copies; do not commit those backup copies.
