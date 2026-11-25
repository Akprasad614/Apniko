# Phase 11 — Apniko Khatha

## Purpose

Apniko Khatha digitises the customer–kirana shop credit arrangement. Each account belongs to exactly one customer and one shop. Credit is never pooled across shops.

## Delivered functionality

### Customer
- View shop-wise offers, credit limit, available credit, outstanding amount, carried credit balance and due date.
- Accept the current version of shopkeeper-offered terms before using credit.
- View an append-only ledger and repayment request history.
- Receive in-app notifications for offers, accepted terms, purchases, reversals, repayments and balance changes.
- Receive a scheduled due-date reminder up to three days before the due date and a one-time overdue reminder.
- Submit a cash-at-shop or UPI repayment notice. A notice remains pending until the shopkeeper verifies receipt; a customer cannot mark their own repayment as paid.

### Shopkeeper
- View customers who have placed orders with the selected shop.
- Offer a credit limit and repayment period (1–90 days).
- Change terms; a terms change increments the version and requires the customer to accept again.
- Pause, reactivate (subject to accepted terms), or close an account only when its balance is zero.
- Confirm/reject repayment requests, record verified cash received, and submit reasoned balance corrections.
- View the complete ledger and repayment history for their own shop only.

### Admin
- Read-only overview of total accounts, active/paused/overdue accounts, pending repayment requests and total outstanding.
- Read-only account ledger and repayment history. The admin UI does not edit customer balances.

## Accounting and security rules

- A purchase is a PURCHASE_DEBIT; a verified repayment is a REPAYMENT_CREDIT.
- An eligible order cancellation creates a separate ORDER_REVERSAL entry. It does not erase or mutate the original purchase entry. Repayment allocations are released by appending allocation-release records. If part of the cancelled order was already repaid, the excess is carried as customer credit and offsets future Khatha purchases.
- Ledger source keys and repayment idempotency keys are unique. Reusing an idempotency key with different amount/channel/reference details is rejected.
- Account balance changes are transactional and account rows are pessimistically locked. Confirmed repayments and credit adjustments are allocated FIFO to original debit entries; order cancellation releases only allocations against that order, preserving other orders' debt. The entity also carries an optimistic version.
- Credit is available only when the shop is active, current terms have been accepted, the account is active, the account is not overdue and available credit remains.
- A repayment request does not change the outstanding balance. The shopkeeper must verify receipt.
- Interest, late fees, automatic bank/UPI deductions and payment mandates are not enabled.
- All terms/status changes, repayment decisions and balance adjustments create audit records. Ledger entries are never edited through an API.
- Khatha lifecycle events use Apniko's existing in-app notification system. A daily 9:00 AM Asia/Kolkata scheduler sends due-date and overdue reminders using idempotent notification source keys; it never initiates or collects a payment.

## Main API surface

### Customer
- GET /api/customer/khatha
- GET /api/customer/khatha/shops/{shopId}
- GET /api/customer/khatha/{accountId}
- GET /api/customer/khatha/{accountId}/ledger
- GET /api/customer/khatha/{accountId}/repayments
- POST /api/customer/khatha/{accountId}/accept
- POST /api/customer/khatha/{accountId}/repayments

### Shopkeeper
- GET /api/shopkeeper/khatha?shopId={shopId}
- GET /api/shopkeeper/khatha/shops/{shopId}/customers
- POST /api/shopkeeper/khatha/offers
- GET /api/shopkeeper/khatha/{accountId}/ledger
- GET /api/shopkeeper/khatha/{accountId}/repayments
- PATCH /api/shopkeeper/khatha/{accountId}/terms
- PATCH /api/shopkeeper/khatha/{accountId}/status
- POST /api/shopkeeper/khatha/{accountId}/repayments
- POST /api/shopkeeper/khatha/{accountId}/repayments/{repaymentId}/confirm
- POST /api/shopkeeper/khatha/{accountId}/repayments/{repaymentId}/reject
- POST /api/shopkeeper/khatha/{accountId}/adjustments

### Admin (read-only)
- GET /api/admin/khatha/overview
- GET /api/admin/khatha/accounts
- GET /api/admin/khatha/{accountId}/ledger
- GET /api/admin/khatha/repayments
- GET /api/admin/khatha/audit-logs

### Checkout
- GET /api/payments/methods?shopId={shopId} advertises KHATHA only when the signed-in customer has usable credit with that shop.
- POST /api/payments/orders/{orderId}/initiate with method KHATHA records the payment as paid only after the locked credit account accepts the debit.

## Database and deployment

The new JPA entities are:
- khatha_accounts (including outstanding and customer credit balances)
- khatha_ledger_entries
- khatha_balance_allocations (FIFO allocation and release records linking repayments/adjustments to original debit entries)
- khatha_repayments
- khatha_audit_logs
- Existing notifications table is reused for Khatha event and reminder notifications.

Development uses the repository's existing Hibernate schema-update workflow. Production uses schema validation and must not rely on Hibernate to create these tables. A production Flyway/SQL migration must be prepared from the actual, backed-up target schema and reviewed by the owner before deployment. No production database was accessed or changed by this phase.

## Operational limitations and future work

- Cash/UPI repayment verification is a shopkeeper workflow; it is not an independent provider settlement integration.
- No auto-debit mandate or recurring collection is created.
- Shopkeeper customer choices are drawn from customers with existing orders at that shop.
- Production rollout requires a reviewed migration, backup/restore validation, real shopkeeper/customer acceptance testing and an agreed dispute-handling process.
