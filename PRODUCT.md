# LocalCart product brief

## Product idea

LocalCart helps independent neighbourhood grocery shops compete in a digital-first market without requiring each shop to build its own online ordering system. Customers discover nearby stores and order everyday essentials. Shopkeepers manage their own catalogue, inventory and incoming orders. Delivery partners handle assigned LocalCart deliveries where the shop's own delivery team is not used.

LocalCart is a shared local-commerce layer, not a restaurant marketplace and not a copy of a quick-commerce interface.

## Primary users

- **Customer:** discovers nearby shops and products, builds a basket, checks out and follows an order.
- **Shopkeeper:** manages their own shop, product availability, inventory and order lifecycle.
- **Delivery partner:** sees available or assigned delivery work and moves an order through pickup and delivery.
- **Administrator:** manages platform-level operations and account provisioning.

## Product principles

1. **Local first:** make independent shops and their neighbourhood presence visible.
2. **Useful over noisy:** prioritise availability, price, order state and next action over decorative dashboards.
3. **Operationally honest:** show stock and order states returned by the backend; do not invent live availability or delivery estimates.
4. **Role boundaries matter:** frontend route guards improve navigation, but backend authorization remains the security boundary.
5. **Designed for real shop work:** management screens should work on a laptop and remain usable on a phone.

## Current backend contract

- Public authentication endpoints are `POST /api/auth/register` and `POST /api/auth/login`.
- Public registration is CUSTOMER-only. Shopkeeper, delivery-partner and administrator accounts are provisioned by LocalCart administration.
- Authentication responses contain `token`, `userId`, `name`, `email` and `role`.
- Role values are `CUSTOMER`, `SHOPKEEPER`, `DELIVERY_PARTNER` and `ADMIN`.
- Customer marketplace endpoints begin under `/api/customer/marketplace`.
- Shopkeeper shop management begins under `/api/shopkeeper`.
- Delivery endpoints begin under `/api/delivery/orders`.

The UI must not assume that every backend capability is already implemented or available in every environment. Confirm DTOs and endpoint responses before building each feature.

## Foundation milestone scope

This milestone establishes the React application, typed routing, API request helper, customer registration/login, role-aware placeholder portals, design tokens and frontend CI. It intentionally does not implement the full catalogue, basket, checkout, payment or operational dashboards yet.
