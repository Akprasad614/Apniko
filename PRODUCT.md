# Apniko product brief

## Product idea

Apniko helps independent neighbourhood grocery shops compete in a digital-first
market without requiring each shop to build its own online ordering system.
Customers discover nearby stores and order everyday essentials. Shopkeepers
manage their own catalogue, inventory and incoming orders. Delivery partners
handle assigned Apniko deliveries where the shop's own delivery team is not used.

Apniko is a shared local-commerce layer, not a restaurant marketplace and not
a copy of a quick-commerce interface.

## Primary users

- **Customer:** discovers nearby shops and products, builds a basket, checks out and follows an order.
- **Shopkeeper:** manages their own shop, product availability, inventory and order lifecycle.
- **Delivery partner:** sees available or assigned delivery work and moves an order through pickup and delivery.
- **Administrator:** manages platform-level operations, product data and account provisioning.

## Product principles

1. **Local first:** make independent shops and their neighbourhood presence visible.
2. **Product information only:** do not use product photos, image URLs, photo placeholders, generated packaging or product-image upload features.
3. **Authentic data:** show only product facts supported by a source or verified operator entry. Unknown values stay unknown.
4. **Useful over noisy:** prioritise availability, price, order state and next action over decorative dashboards.
5. **Operationally honest:** show stock and order states returned by the backend; do not invent live availability or delivery estimates.
6. **Role boundaries matter:** frontend route guards improve navigation, but backend authorization remains the security boundary.
7. **Shop-specific operations:** a master product does not create shop inventory. Each shopkeeper controls their own selling price, stock, availability and low-stock threshold.

## Master product information

Store only verified fields where available:

- Product name and brand
- Category and subcategory
- Exact variant or formulation
- Verified GTIN / barcode
- Printed retail pack size and normalized net quantity/unit
- MRP with source and verification context
- Source record, source URL, licence, attribution and update timestamp

Do not map a nutritional serving size to a retail pack size. Do not map a
source-observed price to shop selling price or MRP. Do not present nutrition
facts when their basis is unknown.

## Current backend contract

- Public authentication endpoints are `POST /api/auth/register` and `POST /api/auth/login`.
- Public registration is CUSTOMER-only. Shopkeeper, delivery-partner and administrator accounts are provisioned by Apniko administration.
- Authentication responses contain `token`, `userId`, `name`, `email` and `role`.
- Role values are `CUSTOMER`, `SHOPKEEPER`, `DELIVERY_PARTNER` and `ADMIN`.
- Customer marketplace endpoints begin under `/api/customer/marketplace`.
- Shopkeeper shop management begins under `/api/shopkeeper`.
- Delivery endpoints begin under `/api/delivery/orders`.

Confirm DTOs and endpoint responses before building each feature.
