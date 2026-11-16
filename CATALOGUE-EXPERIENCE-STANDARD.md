# Apniko catalogue and shopping experience standard

## Product direction

Apniko should deliver the catalogue breadth, visual clarity, product discovery and shopping convenience people expect from leading Indian online grocery services. The implementation is Apniko-owned: do not copy BigBasket, Instamart, Flipkart Minutes or another retailer's protected catalogue database, product descriptions, pricing, photographs, logos or proprietary screen assets without a written licence.

The customer storefront is the primary experience. The shopkeeper catalogue and inventory screens use the same product identity and approved photograph so that both sides refer to the same item.

## Phase 2 — master catalogue and shopping experience

### Master product identity

A shared catalogue product represents one sellable retail variant, not a brand family. Each different pack size, flavour, scent, formulation or other barcode-bearing variant is a separate catalogue product.

Store and display, where available:

- Canonical product name and brand
- Category and subcategory from Apniko's India-first taxonomy
- Verified GTIN / barcode
- Printed pack size and normalized net quantity/unit
- Variant / variant-family relationship
- MRP with its source and verification context
- Source record, source URL, licence, attribution and modification timestamp

A product is not a shop listing. It does not create inventory or set a shop's selling price. A shopkeeper must explicitly list the product in their own shop and set that shop's price, quantity, availability and low-stock threshold.

### Customer experience (highest priority)

- Mobile-first grocery home with a clear location/store context and store selection
- Product-first catalogue cards with a large, square, front-facing product photograph
- Prominent brand, product name, variant and pack size
- Current selected-shop price, optional MRP reference/discount and stock availability
- Fast search across product, brand, pack size and barcode
- Category and subcategory browsing, filters, sorting, paging and useful empty/loading states
- Quantity controls and add-to-cart behaviour that preserve the selected shop's basket rules
- Product detail view should show all available product facts without inventing missing facts
- Consistent photographs between search, category, product detail, cart and order views

Target image presentation: square 1:1 canvas, neutral/white background, contained image, consistent crop, sharp front view and readable pack identity. This is Apniko's presentation target, not a statement of any retailer's private image specification.

### Shopkeeper experience

- Search the shared catalogue by name, brand, category, pack size and barcode
- Show the same approved product photo and variant details used by customers
- Add a product to one selected shop without changing the master catalogue
- Set shop-specific selling price, opening stock and availability
- Display inventory with product photo, brand, pack size, stock, price, low-stock state and listing state
- Keep restock, manual adjustment and movement history available
- Do not expose pending/rejected photo candidates to customer or shopkeeper catalogues

## Phase 3 — product photography and supplier evidence

### Preferred image source order

1. Manufacturer / brand / authorised distributor product feeds with written permission for Apniko's intended display and commercial use
2. Product images supplied directly by participating suppliers or shopkeepers with a documented usage grant
3. Open datasets whose specific image licence permits Apniko's intended use, with required attribution and share-alike obligations
4. No image rather than an unlicensed, unrelated, low-quality or guessed image

Retailer catalogue photographs are not a default source. Do not scrape BigBasket or copy BigBasket images, prices, descriptions or product databases. A retailer's public product page is not itself a permission grant to reproduce its imagery.

### Matching and moderation

For a packaged product, match at minimum:

- Exact GTIN / barcode where the source provides one
- Brand
- Exact product variant / flavour / formulation
- Pack size / net quantity
- Photograph of the actual front of the matching pack

A barcode match by itself is not enough to approve a photograph. Loose produce uses an accurate photograph of the produce category/item and must not be shown as a branded packaged product.

Each photo candidate carries source name, source page, image URL, licence / permission classification, attribution, source product name, source brand, source pack size and (for manufacturer/supplier feeds) a permission reference. Only an administrator-approved photo is exposed to the customer and shopkeeper catalogues. Rejection requires a reason and preserves any previously approved image.

Manufacturer-authorised image records use the `MANUFACTURER_AUTHORIZED` permission classification and must include an internal permission reference. If images are served from a separate CDN, add its exact hostname to `APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS` (comma-separated) or the Spring property `apniko.catalogue.photo-authorized-image-hosts`. Do not allow wildcard hosts.

### Quality gates

Do not optimise for raw catalogue row count. Before a product is customer-visible:

- Product identity is meaningful and not a placeholder/OCR fragment
- Brand, variant and pack size are credible for the product type
- Category/subcategory belongs to Apniko's catalogue taxonomy
- Duplicate GTINs / duplicate natural identities are reconciled
- Any displayed MRP has provenance and is not substituted for the shop's selling price
- Any displayed image is approved and corresponds to the exact product variant
- Source licence/permission and attribution are recorded

Products missing required identity fields remain in admin review rather than being padded with fabricated details or images. Freshness of stock and price is controlled by each shopkeeper, not by an imported retailer dataset.

## Source policy and practical constraints

Public datasets can improve coverage but their records may be incomplete or community-contributed. Each dataset and each image has a separate licence. Review the licence and provenance for the actual snapshot and every media asset before commercial distribution.

Manufacturer/brand images cannot be imported in bulk until a supplier provides an authorised feed, a written image-use grant or another documented permission basis. Apniko must not treat an image being publicly reachable as evidence of permission.

## Release boundary

Phase 2 and Phase 3 can be feature-complete in code, but real catalogue coverage and supplier-authorised imagery depend on access to real supplier/brand data and permission. Do not mark the content rollout complete merely because an importer or UI is implemented. Report separately:

- Code and automated test completion
- Number of source records imported and quality-approved
- Number of products with an approved photograph
- Number of supplier feeds / permission grants actually received
- Products still awaiting manual identity or image review
