# Order fulfilment end-to-end regression

## Purpose

Keep the customer checkout, shopkeeper order desk and delivery-partner workflow aligned with Apniko's per-order fulfilment contract. This work deliberately tests the existing implementation instead of introducing a second fulfilment setting or another dashboard-level preference.

## Source-of-truth behaviour

1. Customer checkout creates a `PLACED` order. It may attach the customer's saved delivery address, but it does not select `SHOP_DELIVERY`, `PLATFORM_DELIVERY` or `CUSTOMER_PICKUP`.
2. The shopkeeper selects and saves fulfilment on the individual order through `PUT /api/shopkeeper/orders/{orderId}/fulfilment?deliveryMode=...`.
3. The order remains `PLACED` after saving. Acceptance is a separate action and must fail until a fulfilment choice has been saved.
4. Once accepted, the fulfilment choice is immutable for that order.
5. A delivery address is required for Shop Delivery and Platform Delivery. Customer Pickup can be saved without one.

## Order status paths

| Fulfilment | Who advances delivery after READY | Expected path |
|---|---|---|
| Shop Delivery | Shopkeeper | `READY → OUT_FOR_DELIVERY → DELIVERED` |
| Customer Pickup | Shopkeeper confirms hand-off | `READY → DELIVERED` |
| Platform Delivery | Assigned delivery partner | `READY → OUT_FOR_DELIVERY → DELIVERED` |

Shop Delivery and Customer Pickup do not require a delivery-partner account. Platform Delivery remains a separate queue assigned to an active delivery partner.

## Automated regression coverage

- The isolated Postman E2E collection verifies customer checkout leaves fulfilment unset.
- It attempts acceptance before the save and expects HTTP 400.
- It saves Platform Delivery for that order and verifies it remains `PLACED`.
- It then accepts the order and verifies the selected mode is retained through delivery-partner accept, pickup and delivery.
- Backend `OrderServiceWorkflowTest` covers Shop Delivery dispatch/completion, Customer Pickup completion, address validation, per-order selection, attempts to alter fulfilment after acceptance, and delivery-partner ownership.

## Safety boundaries

- Postman E2E is restricted to `localhost:8081` / `127.0.0.1:8081`.
- The collection uses the isolated `localcart_test` database described in `PHASE10-README.md`.
- It does not call live Razorpay endpoints.
- It does not modify existing customer orders such as #16 or #17.
