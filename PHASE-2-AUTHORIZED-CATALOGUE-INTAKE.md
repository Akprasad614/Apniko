# Phase 2 — Mendeley packaged-food catalogue intake

**Working branch:** `feature/phase-2-authorized-catalogue-intake`  
**Foundation:** Phase 0 complete; Phase 1 customer experience complete.  
**Dataset version:** Mendeley version 1, DOI `10.17632/rhswn9z9xr.1`.

## Objective

Prepare an audited, source-attributed packaged-food catalogue seed using the
*Indian Packaged Foods Nutritional Composition Dataset (2026)* published on
Mendeley Data. It contains 852 packaged-food records; it is not a complete
grocery catalogue and it does not contain product photographs.

Dataset page:
https://data.mendeley.com/datasets/rhswn9z9xr/1

DOI: https://doi.org/10.17632/rhswn9z9xr.1  
Licence stated in the supplied README and recorded by the NLM Dataset Catalog:
CC BY 4.0  
CSV format: UTF-8, 25 variables.

## Source boundary

- Open Food Facts and related catalogues are excluded.
- Phase 2 accepts only the exact Mendeley version-1 dataset URL, schema and
  source identifier.
- No retailer catalogue fallback, scraped product page, undocumented feed or
  image source is used by the Phase 2 importer.
- The development catalogue remains empty until a local/staging apply is
  explicitly authorised.

## Source-file inspection — complete

The user supplied two CSV copies and one README. The two CSV copies are
byte-identical, not separate dataset versions.

Verified CSV:
- Filename family: `packaged_foods_india.csv`
- Size: 331,383 bytes
- SHA-256: `f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303`
- Encoding: UTF-8
- Rows: 852
- Columns: 25
- `S.No` values are unique and range from 1 to 852.
- All 852 rows contain an item name, brand, category, sub-category,
  ingredients value and serving-size value.

The README's 25 headers were matched exactly. Its fields include product
identity, ingredient text, serving size, nutrition values, `Price_INR`,
source/date/collector metadata and remarks.

### Completeness and data-quality findings

- 9 repeated item-name + brand groups affect 18 rows. The CSV has no verified
  barcode or retail pack size to determine whether those rows are pack
  variants, different offers or duplicated product identities.
- 36 rows contain a numeric field reported with a less-than qualifier such as
  `<0.1`. These values are preserved as text and are not converted to zero.
- 4 numeric cells are malformed/ambiguous: `Proteins_g` contains `7,65`
  and `5.928.8`; `Sodium_mg` contains `9,4`; `Cholesterol_mg` contains
  `4L8`.
- 9 additional source-row consistency alerts were identified: two trans-fat
  values exceed total fat, five dietary-fibre values exceed carbohydrates,
  one serving size is above 500 g, and one calorie value is above 1,000 kcal.
  These are review alerts only; no values are silently corrected.
- 1 row has effectively empty/all-zero nutrition values.
- The category value `ICEREAMES` appears in 37 records. It is preserved as
  supplied and those rows are flagged for taxonomy review; the importer does
  not silently rename it.
- The combined importer review queue contains 67 rows. This count includes
  overlapping reasons and is not the sum of each warning category.

Selected missing-value counts:
- `Calories_kcal`, `Carbohydrates_g`, `Proteins_g` and `Total_Fat_g`:
  1 missing value each.
- `Saturated_Fat_g` and `Trans_Fat_g`: 3 missing values each.
- `Sugar_g` and `Sodium_mg`: 5 each.
- `Dietary_Fiber_g`: 4.
- `Cholesterol_mg`: 13.
- `Calcium_mg`: 6; `Vitamin`: 5; `Iron_mg`: 7.
- `Price_INR`: 7 missing values.
- `Source_of_Data`: 850 missing values; only two rows identify a source.
- `Collection_Date`: 849 missing values; only three dates are populated.
- `Collected_By` and `Remarks`: blank for all 852 rows.

**Important semantic gap:** the README names `Serving_Size_g` and the
nutrition variables but does not say whether the nutrition values are per
serving, per 100 g, or some other basis. Therefore nutrition values remain
verbatim source metadata and must not be presented as validated nutrition
facts until that basis is confirmed. `Serving_Size_g` is not assumed to be
retail net quantity or pack size.

`Price_INR` is retained only inside source provenance. It is never mapped to
Apniko's current shop price or verified MRP. The source/date metadata is too
sparse to make it a live merchant price.

See `PHASE-2-DATASET-INSPECTION.md` for the detailed audit record.

## Rights, licence and attribution

The supplied README and the NLM Dataset Catalog identify this dataset as
CC BY 4.0. The CC BY 4.0 licence permits sharing and adaptation, including
commercial use, provided its conditions are followed: give appropriate
credit, link to the licence and dataset, indicate Apniko's modifications,
and do not imply endorsement. No separate written permission from a
retailer or dataset provider is required for the dataset use described here
under the stated CC BY 4.0 licence.

The importer's `--license-confirmed` and
`--commercial-use-confirmed` switches are operator attestations that the
source files and intended use have been checked against those terms. They are not a request for a separate written permission letter. The dataset DOI is retained as the provenance reference in the
existing backend import contract.

CC BY 4.0 grants only rights the licensor is entitled to grant. If a
particular embedded element is subject to a separate third-party right, that
element must be assessed separately and not treated as automatically cleared.

Dataset attribution used by the importer:

Duddela Sai Prashanth, Ananya Devadiga, Saathwik Saathwik, Priya R Kamath
and Rakshith Bhandary (2026), *Indian Packaged Foods Nutritional Composition
Dataset (2026)*, Version 1, Mendeley Data,
https://doi.org/10.17632/rhswn9z9xr.1, CC BY 4.0.
Apniko modifications: field validation, source-row preservation, duplicate
review flags and product identity normalization.

Licence reference: https://creativecommons.org/licenses/by/4.0/

## Import mapping and storage boundaries

The importer maps only product name, brand, category and sub-category into
the current product model. It deliberately leaves description empty because
the current product description field is limited to 1,000 characters while
some source ingredient strings exceed that length.

The complete original row (ingredients, raw nutrition tokens, serving size,
source price observation, collection metadata and remarks) is preserved in
the private `product_catalogue_source_records.source_metadata_json` field
along with DOI, version, file hash and quality warnings. It is not a current
shop price or customer-facing nutrition display.

Hard rules:

- No GTIN/barcode, retail pack size, net quantity, variant, MRP, current shop
  price, stock or product photo is invented or imported from an unsupported
  field.
- `Serving_Size_g` is not mapped to retail pack size or net quantity.
- Nutrition numbers remain raw strings; do not coerce `<0.1`, comma decimals
  or malformed tokens into numeric values.
- The README's unspecified nutrition basis must be resolved before customer
  nutrition facts are implemented.
- Price observations are retained only in source provenance and not shown as
  current price or MRP.
- Repeated product/brand rows receive separate stable source-row identities
  and are marked `NEEDS_REVIEW`; they are not automatically collapsed.
- `ICEREAMES` category rows and rows with source-data quality alerts are
  also marked `NEEDS_REVIEW`. Category labels are not silently rewritten.
- No shop inventory is created by master-catalogue import. Shopkeepers own
  current price, stock, availability and low-stock thresholds.
- No photo feed is provided by this dataset. Customer product cards remain
  text-first with the neutral no-photo state.

## Importer implementation — complete

The Python importer and backend source gate use only the Mendeley version-1
dataset contract.

The importer:
1. Requires the exact 25-column schema from the supplied README and 852 rows.
2. Validates unique `S.No` values and creates deterministic versioned source
   record IDs.
3. Produces a dry-run report and never downloads the source file.
4. Preserves raw row values and source file hash in source provenance.
5. Uses the source row ID as the idempotent identity discriminator so rows
   with unknown package variants are not incorrectly merged.
6. Sends ambiguous and low-quality rows to admin moderation.
7. Defaults to dry-run. Applying requires an admin JWT and explicit
   operator confirmation of the CC BY 4.0 licence and its applicability to
   Apniko's intended commercial use.
8. The backend accepts only this exact Mendeley dataset URL and CC-BY-4.0
   source identifier; arbitrary source URLs are rejected.

The backend uses Hibernate `ddl-auto=update` to add the private
`source_metadata_json` column on next local backend startup. No database
import or reset has been run as part of this code change.

## Local dry-run — complete

The user ran the full CSV with the Phase 2 importer:
- 852 source rows read; 852 product records eligible for review/import.
- 9 repeated name+brand groups affecting 18 rows.
- 67 rows marked for administrator review.
- 4 rows have malformed numeric cells.
- 36 rows contain less-than numeric qualifiers.
- 1 row has empty/all-zero nutrition fields.
- 0 images, 0 barcodes, 0 retail pack sizes/net quantities, 0 current shop
  prices and 0 MRPs are populated.
- Dry-run made no API calls and no database changes.

No products have been imported. The local catalogue remains empty.

## Phase 2 exit criteria

- [x] Exact CSV and README inspected; schema, row count and SHA-256 recorded.
- [x] Data-quality gaps, duplicates, malformed numeric tokens and sparse
      collection provenance documented.
- [x] CC BY 4.0 attribution and use conditions documented.
- [x] Mendeley-only importer and backend source validation implemented.
- [x] Python importer/reset tests and backend verification pass on the
      committed Phase 2 implementation.
- [x] Full local source CSV dry-run completed without API or database changes.
- [ ] Operator runs the apply command only after confirming the CC BY 4.0
      conditions and attribution will be met.
- [ ] Any local/staging import is completed only after explicit owner approval.
- [ ] Imported records are reviewed and approved by an administrator.
- [ ] Customer nutrition facts remain withheld until the nutrition basis is
      confirmed and a typed, validated model is implemented.

**Current status:** Phase 2 importer implementation, dataset inspection,
local dry-run and automated verification are complete. The seed import has
not been applied, so the development catalogue remains empty. This does not
block Phase 3 engineering; image sourcing is a separate workstream because
this dataset contains no product photographs.
