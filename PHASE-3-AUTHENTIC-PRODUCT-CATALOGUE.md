# Phase 3 — Authentic Indian Grocery Product Catalogue

## Scope

Build an auditable, searchable Indian grocery master catalogue using only
approved product-information sources. Apniko stores product information only;
product photographs and image features are excluded.

## Implemented

- Mendeley version-1 dataset inspection and source attribution.
- Source-specific 25-column / 852-row CSV validation.
- Exact inspected CSV SHA-256 pinned for apply mode.
- Stable source-row identifiers and private source provenance.
- Raw source values preserved without numeric guesswork.
- No mapping of `Price_INR` to shop price or verified MRP.
- No mapping of `Serving_Size_g` to retail pack size.
- No invented GTINs, pack sizes, MRP, current shop price or stock.
- Backend source gate accepts only the exact Mendeley source identity and licence.
- All Mendeley rows are forced to `NEEDS_REVIEW`.
- Admin publication API blocks Mendeley rows without verified GTIN and retail pack size.
- Python importer, test suite and Phase 3 verification script updated.

## Dataset audit

| Check | Result |
|---|---:|
| Records | 852 |
| Columns | 25 |
| File size | 331,383 bytes |
| CSV SHA-256 | `f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303` |
| Unique source IDs | 852 |
| Repeated item/brand groups | 9 groups / 18 rows |
| Malformed numeric rows | 4 |
| Rows with less-than numeric values | 36 |
| Additional source consistency / taxonomy flags | Included in the source-quality review |
| Rows requiring retail identity verification | 852 |

The dataset has no verified retail GTIN/barcode or retail pack-size fields.
Its nutrition basis is unspecified and row-level collection provenance is
sparse. Its observed price values are not current local-shop prices or
verified MRP. This makes it suitable only as a reference-candidate seed, not
as a complete sellable retail catalogue.

## Import status

The source CSV has been inspected and the importer is ready for a guarded
apply. **No product rows were imported into the database by this Phase 3–4
code change.** The repository connection does not provide access to the
developer's local MySQL instance or administrator token. The local catalogue
was previously verified empty after a backup-first reset.

## Remaining Phase 3 content blocker

A complete authentic retail catalogue still needs an approved retail product
source that provides reliable sellable variant identity (brand, product name,
GTIN/barcode and retail pack size), category mapping, licence/commercial-use
terms, attribution and update rules. Open Food Facts, BigBasket scraping and
unapproved dataset mirrors remain excluded. The Mendeley source alone cannot
satisfy this requirement. Until a suitable source is admitted, keep the
catalogue empty or retain imported Mendeley candidates in `NEEDS_REVIEW`.

## Acceptance

- [x] Dataset inspected and checksum verified.
- [x] Importer and backend source gate implemented.
- [x] All imported records default to review, not customer-visible.
- [x] Publishing incomplete Mendeley retail identities is blocked server-side.
- [x] No retail facts are inferred from nutrition/serving-size/source-price fields.
- [x] Phase 3 automated checks added.
- [ ] Approved complete retail SKU source selected and legally reviewed.
- [ ] Genuine retail catalogue records populated and verified.
