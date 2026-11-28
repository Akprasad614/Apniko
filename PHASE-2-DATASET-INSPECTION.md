# Phase 2 — source dataset inspection report

**Dataset:** Indian Packaged Foods Nutritional Composition Dataset (2026)  
**Repository:** Mendeley Data  
**Version:** 1  
**DOI:** `10.17632/rhswn9z9xr.1`  
**Licence stated in supplied README:** CC BY 4.0  
**Inspection date:** 2026-11-22

## Source files supplied

The user supplied:
- `packaged_foods_india-Ieivcg(1).csv`
- `packaged_foods_india(1).csv`
- `Readme(1).md`

The two CSV uploads are byte-for-byte identical.

| Check | Observed result |
|---|---:|
| CSV size | 331,383 bytes |
| SHA-256 | `f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303` |
| Encoding | UTF-8 |
| Rows | 852 |
| Columns | 25 |
| `S.No` | Unique values 1–852 |
| Exact README header match | Yes |

The supplied README describes 852 records, 25 variables and a UTF-8 CSV. It lists
product name, brand, category, sub-category, ingredients, serving size,
nutrition facts, `Price_INR` and collection metadata.

## Column completeness

| Column(s) | Missing rows |
|---|---:|
| `S.No`, `Item name`, `Brand_Name`, `Category`, `Sub_Category`, `Ingredients`, `Serving_Size_g` | 0 each |
| `Calories_kcal`, `Carbohydrates_g`, `Proteins_g`, `Total_Fat_g` | 1 each |
| `Saturated_Fat_g`, `Trans_Fat_g` | 3 each |
| `Sugar_g`, `Sodium_mg` | 5 each |
| `Dietary_Fiber_g` | 4 |
| `Cholesterol_mg` | 13 |
| `Calcium_mg` | 6 |
| `Vitamin` | 5 |
| `Iron_mg` | 7 |
| `Price_INR` | 7 |
| `Source_of_Data` | 850 |
| `Collection_Date` | 849 |
| `Collected_By`, `Remarks` | 852 each |

Only two records populate `Source_of_Data` (ZEPTO and BLINKIT) and only three
populate `Collection_Date` (23-10-2025, 27-10-2025 and 28-10-2025). The
collector and remarks columns are entirely blank. Provenance at row level is
therefore incomplete.

## Duplicate and identity findings

No full CSV rows are exact duplicates. There are 9 repeated combinations of
normalized `Item name` + `Brand_Name`, affecting 18 rows. Examples include
Britannia Milk Bikis Biscuits, Currychief Madras Sambar, Dairy Day Black
Currant Ice Cream Tub, both Lay's chips varieties, Nutrisum Oats Cookies and
Raw Pressery Valencia Orange Fruit Juice.

The source has no barcode/GTIN or retail pack-size column. These rows cannot
safely be assumed to represent the same retail item. The importer therefore
uses the versioned `S.No` as the stable identity discriminator and places
repeated item/brand groups in `NEEDS_REVIEW`; it does not collapse them.

## Numeric and taxonomy quality

- 36 rows contain one or more less-than numeric tokens such as `<0.1`.
  These are retained verbatim, not changed to zero.
- Four malformed numeric cells were found:
  - `Proteins_g`: `7,65` and `5.928.8`
  - `Sodium_mg`: `9,4`
  - `Cholesterol_mg`: `4L8`
- One row has effectively empty/all-zero nutrition values.
- Review alerts include trans fat exceeding total fat, dietary fibre exceeding
  carbohydrates, a serving size above 500 g and a calorie value above
  1,000 kcal. The affected values are not corrected automatically.
- The category value `ICEREAMES` occurs 37 times. It is preserved exactly
  and those rows are flagged for category review instead of being silently
  renamed.

The Phase 2 dry-run importer currently flags 67 rows for administrator review.
This is the combined count after overlapping quality reasons are accounted
for; it is not the sum of each issue category.

## Important semantics not supplied by the README

The README does not say whether calorie and nutrient values are expressed per
serving, per 100 g, or on another basis. A `Serving_Size_g` column does not
resolve that question by itself. Until this is confirmed, nutrient values
remain raw source strings in private provenance and must not be exposed as
validated nutrition facts.

The `Price_INR` column is not mapped to current shop prices or MRP. Its row
level source/date metadata is too sparse to treat it as a current price for
any Apniko shop.

The largest ingredient string is 1,387 characters, while the current product
description field allows 1,000. Ingredients are not truncated into product
descriptions; the original text is preserved in source metadata.

## Importer mapping and safeguards

Product master fields mapped:
- `Item name` → product name
- `Brand_Name` → brand
- `Category` → category
- `Sub_Category` → sub-category

The original row (including ingredients, raw nutrition tokens, serving size,
price observation and collection metadata) is preserved in the private
`source_metadata_json` field of the catalogue source record, along with the
dataset DOI/version, file SHA-256 and quality warnings.

Not imported as product/shop facts:
- No barcode/GTIN
- No retail pack size or net quantity
- No variant inference
- No MRP/current selling price from `Price_INR`
- No product images
- No nutrition facts displayed to customers

The importer defaults to dry-run and requires an admin JWT plus explicit
operator confirmation of the CC BY 4.0 licence and its applicability to
Apniko's intended commercial use before applying. No data was imported during
this inspection.
