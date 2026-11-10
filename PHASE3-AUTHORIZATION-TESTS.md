# LocalCart Phase 3 — Authorization and business-logic regression tests

This phase adds focused JUnit/Mockito regression tests around existing LocalCart authorization boundaries. It does not change application behavior, database schema, endpoint contracts, or production configuration.

## Coverage added

- Customer order IDOR checks: listing another customer's orders, cancelling another customer's order, and reading another customer's order history.
- Shopkeeper isolation: querying another shop's orders and accessing inventory/update/restock/movement-history IDs outside the shop they own.
- Delivery-partner isolation: delivering or taking over an order assigned to a different partner, use of delivery service methods by a shopkeeper, and inactive partner access.
- Payment isolation: verifying another customer's payment and reading payment details from an inactive account.

## Run the new tests locally

```bash
./mvnw -B -ntp -Dtest=OrderControllerAuthorizationRegressionTest,ShopInventoryAuthorizationRegressionTest,DeliveryOrderAuthorizationRegressionTest,PaymentServiceAuthorizationRegressionTest test
```

Run the full backend verification before merging:

```bash
./mvnw -B -ntp verify
```

The focused tests use mocks and do not call Razorpay or a production database. The repository's existing CI continues to run the complete Maven verification against its disposable MySQL service.

## Important testing boundary

These are service/controller regression tests, not a complete HTTP penetration test. Route-filter integration tests and concurrency tests against a disposable MySQL instance remain candidates for a later stage. Passing tests show that the tested paths reject the covered unauthorized actions; they do not prove the entire application is vulnerability-free.
