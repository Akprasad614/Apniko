# Apniko catalogue and shopping experience standard

## Product direction

Apniko is a product-information-only local grocery marketplace. **Do not use
product photographs anywhere in the customer, shopkeeper, delivery or admin
product experience.** No image URL, photo upload, product-image placeholder,
image gallery, image attribution, generated pack art or image moderation is
part of the product.

The customer storefront is the primary shopping experience. Customer and
shopkeeper screens use the same canonical product identity and the same
text-based product facts.

## Master product identity

A master product represents one sellable retail variant, not a brand family.
Different pack sizes, flavours, formulations or barcode-bearing variants are
separate catalogue entries when the source supports that distinction.

Store and display only when verified:
- Canonical product name and brand
- Category and subcategory from Apniko's India-first taxonomy
- GTIN / barcode
- Printed retail pack size and normalized net quantity/unit
- Variant or variant-family relationship
- MRP with its source and verification context
- Source record, source URL, licence, attribution and modification timestamp

A product is not a shop listing. It does not create inventory or set a shop's
selling price. A shopkeeper explicitly lists the product in their own shop
and sets that shop's price, quantity, availability and low-stock threshold.

## Customer experience

- Mobile-first grocery home with clear location and selected-shop context.
- Text-first product cards with product name, brand, variant and pack size.
- Selected-shop selling price and stock availability from live shop inventory.
- Search across product, brand, pack size and barcode where available.
- Category and subcategory browsing, filters, sorting, paging and useful
  loading and empty states.
- Quantity controls and add-to-cart behaviour that preserve selected-shop
  basket rules.
- Product detail views show only available, supported product facts.
- Cart and order views use text product identity and quantities.

## Shopkeeper experience

- Search the shared catalogue by name, brand, category, pack size and barcode.
- Add a product to one selected shop without changing the master catalogue.
- Set shop-specific selling price, opening stock and availability.
- Display inventory with product name, brand, pack size, stock, price,
  low-stock state and listing state.
- Keep restock, manual adjustment and movement history available.
- Unknown master-product fields remain visibly unspecified; do not infer them.

## Catalogue quality and publication gate

A product must not become customer-visible merely because it came from an
importer. Before publication:
- Product identity is meaningful and not a placeholder/OCR fragment.
- Brand and category are supported by the source.
- Sellable retail variant is clear.
- Verified GTIN/barcode and retail pack size are present for packaged goods.
- Duplicate GTINs and natural identities are reconciled.
- Any MRP has its own reliable source and is not substituted for shop price.
- Source rights, attribution and modification records are retained.

Mendeley rows are reference candidates only. They lack GTIN and retail pack
size and must remain `NEEDS_REVIEW`. The backend prevents their publication
until those facts are supplied by an approved source.

## Price and inventory separation

The master catalogue does not set local price or stock. Each shopkeeper
controls the shop's selling price, on-hand quantity, availability and
low-stock threshold. Imported historical source prices are never shown as
current local prices.
