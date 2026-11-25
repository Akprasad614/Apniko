# Apniko payments and Khatha — staging test plan

Use staging-only synthetic users and Razorpay test mode. Do not use real payment credentials or real customer financial records. This document defines cases; it does not claim those tests have run.

## Razorpay sandbox

| ID | Scenario | Expected invariant |
|---|---|---|
| PAY-01 | Create payment order | INR minor-unit amount and local order mapping match exactly |
| PAY-02 | Successful payment + server verify | Paid only after trusted server verification |
| PAY-03 | Invalid signature | Rejected; no paid state |
| PAY-04 | Wrong amount/currency/order | Rejected; no cross-order settlement |
| PAY-05 | Duplicate webhook | Idempotent; no double settlement |
| PAY-06 | Replayed/out-of-order webhook | State transition remains valid and monotonic |
| PAY-07 | Payment failure/cancel/timeout | Order and payment remain consistent; retry policy respected |
| PAY-08 | Full and partial refund | Provider result and local refund ledger reconcile |
| PAY-09 | Duplicate refund request | No second refund |
| PAY-10 | Provider 4xx/5xx/network timeout | Safe error, retry/reconciliation path, no false success |
| PAY-11 | Logs and exception paths | No secret, auth header, signature or full payload disclosure |

## Khatha

| ID | Scenario | Expected invariant |
|---|---|---|
| KH-01 | Eligible account credit purchase | Limit/outstanding policy enforced |
| KH-02 | Exceed credit limit | Atomic rejection; no partial ledger write |
| KH-03 | Concurrent requests at limit | Lock/transaction guarantees no over-extension |
| KH-04 | Full/partial repayment | Append-only ledger and correct outstanding calculation |
| KH-05 | Duplicate repayment callback/reference | Idempotent; one credit only |
| KH-06 | Reversal | Compensating ledger entry; original entry immutable |
| KH-07 | Unauthorized cross-account access | Forbidden; no data leakage |
| KH-08 | Inactive/blocked account | Financial operation denied |
| KH-09 | Zero/negative/overflow amounts | Validation rejects invalid values |
| KH-10 | Statement/reconciliation | Ledger sum equals displayed account balance and related payment records |
| KH-11 | Audit trail | Actor, operation, timestamps and references traceable without sensitive data |
| KH-12 | Simulated concurrent repayment | No duplicate credit and deterministic final balance |

For every test attach a redacted request/response or test report, relevant synthetic account IDs and timestamp. Never attach API keys, passwords, webhook secrets, JWTs, card/bank details or personal customer information. Reconcile payment provider sandbox events with local payment/order/Khatha ledgers after the run.
