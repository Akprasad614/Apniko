# Phase 2 — Mendeley packaged-food catalogue intake

**Working branch:** `feature/phase-2-authorized-catalogue-intake`  
**Foundation:** Phase 0 complete; Phase 1 customer experience complete.  
**Starting foundation commit:** `e5cadccd01806cdd4186812bc55b143e6552f249`

## Objective

Prepare a clean, source-attributed packaged-food catalogue seed using the
*Indian Packaged Foods Nutritional Composition Dataset (2026)* published on
Mendeley Data. This is a limited packaged-food seed, not a complete grocery
catalogue.

Dataset landing page:
https://data.mendeley.com/datasets/rhswn9z9xr/1

DOI: https://doi.org/10.17632/rhswn9z9xr.1  
Version: 1  
Published: 23 June 2026  
Repository page states: CC BY 4.0  
Described coverage: 852 packaged-food records.

The repository page and catalogue metadata are not a substitute for
inspecting the exact downloadable files. The actual CSV and README have not
yet been reviewed in this branch. No importer may be applied and no product
rows may be added until those files are inspected.

## Excluded sources and old importer

- Open Food Facts and all related catalogues are prohibited.
- BigBasket and its website listings, Kaggle copy, images and undocumented APIs
  are excluded completely.
- The existing BigBasket-specific importer, its BigBasket-only backend source
  validation, and its BigBasket-specific tests are legacy implementation.
  Replace or remove them in this phase; do not run them and do not retain a
  BigBasket fallback.
- The empty development catalogue must remain empty until the reviewed
  Mendeley importer is ready and an explicit local/staging apply is approved.

## Rights and provenance review

The Mendeley landing page identifies a CC BY 4.0 licence. Before relying on
that grant for a commercial Apniko catalogue, inspect the actual README and
dataset file notes for excluded components, third-party content, attribution
requirements and the submitters' stated provenance. The licence grants only
rights the licensor is entitled to grant; do not assume Mendeley independently
verified every row or underlying asset. Preserve the citation, licence link,
dataset version, source row identifier and all transformations. Do not imply
endorsement by Mendeley or dataset contributors.

Suggested attribution, to be finalised against the supplied README:

Duddela Sai Prashanth, Ananya Devadiga, Saathwik Saathwik, Priya R Kamath
and Rakshith Bhandary (2026), *Indian Packaged Foods Nutritional Composition
Dataset (2026)*, Version 1, Mendeley Data,
https://doi.org/10.17632/rhswn9z9xr.1, CC BY 4.0.
Apniko modifications must be disclosed, including validation, normalization,
deduplication and category mapping.

## Required source-file inspection

Before changing importer code, inspect the original CSV and README and record:

1. Exact filenames, file hashes, encoding, delimiter and column headers.
2. Actual row count and whether the advertised 852 rows match.
3. Null rates, duplicate identifiers/names and malformed records.
4. Exact meanings and units of all serving-size, nutrition, price and category
   fields, using the README and dataset notes rather than guessing.
5. Source provenance and whether any row or component has separate rights or
   exclusions.

Keep source files local and ignored. Do not commit the dataset, credentials or
private permission correspondence. Do not fetch product imagery from other
sites to fill missing fields.

## Field-mapping rules

Map only inspected and understood columns. Candidate product identity fields
include product name, brand, category/subcategory and source row identifier
where present. Nutritional composition and ingredient fields may be retained
as labelled product metadata only where the schema supports them and the
units/meaning are clear.

Hard rules:

- A nutritional serving size is not the product's retail pack size.
- Never invent a GTIN, barcode, pack size, variant, MRP, image or other missing
  fact.
- Dataset prices, if present, are observations from that dataset and must not
  become a current shop price or verified MRP.
- Shop-specific price, stock, availability and low-stock thresholds remain
  shopkeeper-managed inventory data.
- Do not turn uncertain rows into active customer-visible products. Route
  incomplete or ambiguous records to admin review.
- Do not create inventory merely because a master product was imported.
- Do not populate customer cards with dataset-only fields that have not been
  verified for customer display.

## Importer implementation requirements

After source-file inspection:

1. Replace the BigBasket-specific Python CLI with a Mendeley-file importer
   whose default is a local dry run and which never downloads the dataset.
2. Validate required columns and value semantics; report accepted, rejected,
   duplicate and missing-field counts.
3. Use stable source identifiers when provided; otherwise use a documented
   deterministic identity strategy and test collision/duplicate behaviour.
4. Retain dataset title, DOI, version, CC BY 4.0 licence reference, attribution,
   source row identity and transformation notes in source provenance.
5. Exclude prices from current shop pricing and exclude all image fields.
6. Gate apply behind an administrator token and explicit local/staging
   confirmation. Production import is outside this phase.
7. Keep imports idempotent and batch-limited. Preserve manual admin review
   decisions on later imports.
8. Add unit tests for schema mismatch, missing values, duplicates, malformed
   nutrition units, dry-run isolation, apply gating, attribution and backend
   provenance validation.

If the actual files do not support a proposed field, omit it. Do not fabricate
a replacement.

## Customer presentation

Phase 1's owner-approved Color Pop text-first customer presentation remains
the baseline. Abstract category marks are category decoration only, not
photographs of specific products. Products without authorised exact-pack
photographs remain text-first with the neutral no-photo state. This phase does
not activate a third-party product-image source.

## Phase 2 exit criteria

- [ ] Actual Mendeley CSV and README inspected; schema, 852-row count, file
      hashes, field meanings, units and provenance recorded.
- [ ] CC BY 4.0 attribution and any third-party/excluded-content caveats
      documented from the supplied dataset files.
- [ ] BigBasket-specific importer, backend source gate and tests replaced or
      removed; no BigBasket fallback remains.
- [ ] Mendeley importer defaults to dry-run, validates inputs, deduplicates,
      retains provenance and passes its unit tests.
- [ ] Dry-run report reviewed; rejected and ambiguous rows accounted for.
- [ ] Isolated local/staging import is performed only after explicit approval.
- [ ] Imported rows pass quality checks and administrator moderation.
- [ ] No shop inventory, current shop price, stock, GTIN, retail pack size or
      product photograph is invented or populated from an unsupported field.
- [ ] Empty catalogue and text-first customer UI behaviour continue to pass.
- [ ] Backend, frontend, importer and security CI pass on the final Phase 2
      commit.

**Current status:** Phase 2 planning is updated to the Mendeley dataset.
Source-file inspection and importer migration are not complete because the
actual CSV and README have not yet been reviewed. No products have been
imported. The local catalogue remains empty.
