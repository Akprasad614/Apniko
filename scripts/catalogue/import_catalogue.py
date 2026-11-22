#!/usr/bin/env python3
"""Validate and import the Mendeley Indian Packaged Foods dataset into Apniko.

Dry-run is the default. The script never downloads the dataset. Ingredients,
nutrition values and observed source prices are retained in private provenance
metadata, not mapped to product descriptions, shop prices, MRP or pack sizes.
Applying requires explicit licence/commercial-use confirmation and an admin JWT.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit

import requests

SOURCE_NAME = "Mendeley Indian Packaged Foods Dataset (2026)"
SOURCE_LICENSE = "CC-BY-4.0"
SOURCE_URL = "https://data.mendeley.com/datasets/rhswn9z9xr/1"
DOI = "10.17632/rhswn9z9xr.1"
DATASET_TITLE = "Indian Packaged Foods Nutritional Composition Dataset (2026)"
DATASET_VERSION = "1"
EXPECTED_ROWS = 852
MAX_BATCH_SIZE = 250

EXPECTED_HEADERS = (
    "S.No", "Item name", "Brand_Name", "Category", "Sub_Category",
    "Ingredients", "Serving_Size_g", "Calories_kcal", "Carbohydrates_g",
    "Proteins_g", "Total_Fat_g", "Saturated_Fat_g", "Trans_Fat_g",
    "Sugar_g", "Sodium_mg", "Dietary_Fiber_g", "Cholesterol_mg",
    "Calcium_mg", "Vitamin", "Iron_mg", "Price_INR", "Source_of_Data",
    "Collection_Date", "Collected_By", "Remarks",
)
NUMERIC_FIELDS = (
    "Serving_Size_g", "Calories_kcal", "Carbohydrates_g", "Proteins_g",
    "Total_Fat_g", "Saturated_Fat_g", "Trans_Fat_g", "Sugar_g",
    "Sodium_mg", "Dietary_Fiber_g", "Cholesterol_mg", "Calcium_mg",
    "Iron_mg", "Price_INR",
)
NUTRIENT_FIELDS = (
    "Calories_kcal", "Carbohydrates_g", "Proteins_g", "Total_Fat_g",
    "Saturated_Fat_g", "Trans_Fat_g", "Sugar_g", "Sodium_mg",
    "Dietary_Fiber_g", "Cholesterol_mg", "Calcium_mg", "Vitamin", "Iron_mg",
)
NUMERIC_TOKEN = re.compile(r"^(?:<\s*)?\d+(?:\.\d+)?$")


def clean(value: object) -> str:
    if value is None:
        return ""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", str(value))).strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def numeric_kind(value: object) -> str:
    raw = clean(value)
    if not raw:
        return "missing"
    if NUMERIC_TOKEN.fullmatch(raw):
        return "less_than" if raw.startswith("<") else "numeric"
    return "malformed"


def decimal_value(value: object) -> float | None:
    raw = clean(value)
    if not raw or raw.startswith("<"):
        return None
    try:
        return float(raw)
    except ValueError:
        return None


def quality_warnings(row: dict[str, str], repeated_identity: bool) -> list[str]:
    warnings: list[str] = []
    malformed = [field for field in NUMERIC_FIELDS if numeric_kind(row.get(field)) == "malformed"]
    if malformed:
        warnings.append("Malformed numeric source value(s): " + ", ".join(malformed))

    serving = decimal_value(row.get("Serving_Size_g"))
    calories = decimal_value(row.get("Calories_kcal"))
    total_fat = decimal_value(row.get("Total_Fat_g"))
    trans_fat = decimal_value(row.get("Trans_Fat_g"))
    carbs = decimal_value(row.get("Carbohydrates_g"))
    fiber = decimal_value(row.get("Dietary_Fiber_g"))

    # Review alerts only. The README does not specify whether nutrition is per
    # serving or per 100 g, so values are never corrected or numerically coerced.
    if serving is not None and serving > 500:
        warnings.append("Reported serving size exceeds 500 g; verify source label")
    if calories is not None and calories > 1000:
        warnings.append("Reported calories exceed 1,000 kcal; verify source label")
    if total_fat is not None and trans_fat is not None and trans_fat > total_fat:
        warnings.append("Reported trans fat exceeds total fat; verify source label")
    if carbs is not None and fiber is not None and fiber > carbs:
        warnings.append("Reported dietary fibre exceeds carbohydrates; verify source label")

    if clean(row.get("Category")).casefold() == "icereames":
        warnings.append("Source category ICEREAMES appears misspelled; confirm taxonomy before activation")

    present = [clean(row.get(field)) for field in NUTRIENT_FIELDS]
    present = [value for value in present if value]
    if not present or all(value in {"0", "0.0", "0.00"} for value in present):
        warnings.append("Nutrition fields are empty or all reported numeric facts are zero")
    if repeated_identity:
        warnings.append("Repeated item name and brand in source; retail pack/variant is not verified")
    return warnings


def make_source_metadata(row: dict[str, str], row_id: int, file_hash: str,
                         warnings: list[str]) -> str:
    # All source cell values stay strings. '<0.1', comma decimals and malformed
    # tokens are preserved exactly rather than rounded, guessed or discarded.
    payload = {
        "dataset": {
            "title": DATASET_TITLE,
            "doi": DOI,
            "version": DATASET_VERSION,
            "license": "CC BY 4.0",
            "fileSha256": file_hash,
            "sourceRowId": row_id,
        },
        "sourceValues": {header: row.get(header, "") for header in EXPECTED_HEADERS},
        "fieldHandling": {
            "Serving_Size_g": "Source serving-size field only; not retail pack size or net quantity",
            "nutrition": "Raw source values retained; README does not specify per-serving or per-100-g basis",
            "Price_INR": "Dataset observation only; not current shop price or verified MRP",
            "Ingredients": "Retained as source text; backend has no separately verified ingredient display field",
        },
        "qualityWarnings": warnings,
    }
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def load_mendeley_csv(path: Path, limit: int = 0) -> tuple[list[dict], dict]:
    if not path.is_file():
        raise ValueError(f"CSV file not found: {path}")
    if limit < 0:
        raise ValueError("--limit must be zero or a positive integer")

    file_hash = sha256_file(path)
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = tuple(reader.fieldnames or ())
        missing = sorted(set(EXPECTED_HEADERS) - set(headers))
        extra = sorted(set(headers) - set(EXPECTED_HEADERS))
        if missing or extra:
            raise ValueError(
                "CSV schema does not match supplied README. "
                f"Missing columns: {missing or 'none'}; extra columns: {extra or 'none'}"
            )
        rows = []
        for line_number, row in enumerate(reader, start=2):
            if None in row:
                raise ValueError(f"CSV row {line_number} contains extra/unmapped columns")
            rows.append({header: (row.get(header) or "") for header in EXPECTED_HEADERS})

    row_count = len(rows)
    if row_count != EXPECTED_ROWS:
        raise ValueError(f"Expected {EXPECTED_ROWS} data rows from README; found {row_count}")

    ids = []
    for line_number, row in enumerate(rows, start=2):
        raw_id = clean(row.get("S.No"))
        if not raw_id.isdigit() or int(raw_id) <= 0:
            raise ValueError(f"CSV row {line_number} has invalid S.No: {raw_id!r}")
        ids.append(int(raw_id))
        if not clean(row.get("Item name")):
            raise ValueError(f"CSV row {line_number} has no Item name")
        if not clean(row.get("Category")) or not clean(row.get("Sub_Category")):
            raise ValueError(f"CSV row {line_number} has no source category/sub-category")
    if len(set(ids)) != len(ids):
        raise ValueError("S.No contains duplicates; refusing unstable source-record identifiers")

    identities = [
        (clean(row["Item name"]).casefold(), clean(row["Brand_Name"]).casefold())
        for row in rows
    ]
    identity_counts = Counter(identities)
    repeated = {key for key, count in identity_counts.items() if count > 1}
    repeated_rows = sum(identity_counts[key] for key in repeated)

    records = []
    review_rows = malformed_rows = less_than_rows = empty_nutrition_rows = 0
    for row in rows[:limit or None]:
        row_id = int(row["S.No"])
        identity = (clean(row["Item name"]).casefold(), clean(row["Brand_Name"]).casefold())
        warnings = quality_warnings(row, identity in repeated)
        malformed = any(numeric_kind(row.get(field)) == "malformed" for field in NUMERIC_FIELDS)
        less_than = any(numeric_kind(row.get(field)) == "less_than" for field in NUMERIC_FIELDS)
        if malformed:
            malformed_rows += 1
        if less_than:
            less_than_rows += 1
        if any("Nutrition fields are empty" in warning for warning in warnings):
            empty_nutrition_rows += 1
        review_reason = "; ".join(warnings) if warnings else None
        if review_reason:
            review_rows += 1

        source_record_id = f"mendeley-ipf-2026-v1:{row_id:04d}"
        records.append({
            "name": clean(row["Item name"])[:120],
            "brand": clean(row["Brand_Name"])[:100] or None,
            "barcode": None,
            "packSize": None,
            "netQuantity": None,
            "netQuantityUnit": None,
            "variantGroupKey": None,
            "description": None,
            "category": clean(row["Category"])[:80],
            "subcategory": clean(row["Sub_Category"])[:100],
            "mrp": None,
            "mrpSource": None,
            "sourceRecordId": source_record_id,
            "sourceModifiedAt": None,
            "sourceMetadataJson": make_source_metadata(row, row_id, file_hash, warnings),
            "identityDiscriminator": source_record_id,
            "sourceReviewReason": review_reason,
        })

    stats = {
        "source_rows": row_count,
        "received": row_count if limit == 0 else min(row_count, limit),
        "eligible": len(records),
        "duplicate_identity_groups": len(repeated),
        "duplicate_identity_rows": repeated_rows,
        "review_required_rows": review_rows,
        "rows_with_malformed_numeric_values": malformed_rows,
        "rows_with_less_than_values": less_than_rows,
        "rows_with_empty_or_zero_nutrition": empty_nutrition_rows,
        "dataset_sha256": file_hash,
        "missing_by_field": {
            field: sum(not clean(row.get(field)) for row in rows)
            for field in EXPECTED_HEADERS
        },
    }
    return records, stats


def validate_api_url(value: str) -> str:
    parsed = urlsplit(value)
    host = (parsed.hostname or "").lower()
    local = host in {"localhost", "127.0.0.1", "::1"}
    if parsed.scheme != "https" and not (parsed.scheme == "http" and local):
        raise ValueError("Apniko API must use HTTPS, or HTTP on localhost only")
    if not host or parsed.username or parsed.password:
        raise ValueError("Apniko API URL is invalid")
    return value.rstrip("/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="Locally held Mendeley CSV; never downloaded")
    parser.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    parser.add_argument("--limit", type=int, default=0, help="Optional row limit; 0 validates all 852 rows")
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--license-confirmed", action="store_true",
                        help="Confirm the supplied dataset files identify CC BY 4.0 and no applicable exclusion")
    parser.add_argument("--commercial-use-confirmed", action="store_true",
                        help="Confirm the CC BY 4.0 grant covers Apniko's intended commercial use")
    parser.add_argument("--permission-reference", default=DOI)
    parser.add_argument("--apply", action="store_true", help="Submit validated records to the admin import API")
    return parser.parse_args()


def run(args: argparse.Namespace, session: requests.Session | None = None) -> int:
    if args.limit < 0 or not 1 <= args.batch_size <= MAX_BATCH_SIZE:
        print("Use --limit >= 0 and --batch-size 1..250.", file=sys.stderr)
        return 2
    records, stats = load_mendeley_csv(args.file, args.limit)
    print("Source:", SOURCE_NAME)
    print("DOI:", DOI)
    print("Licence: CC BY 4.0 (as stated in the supplied README)")
    print("Mode:", "APPLY" if args.apply else "DRY RUN")
    print("Source rows:", stats["source_rows"])
    print("Rows in this run:", stats["received"])
    print("Eligible product records:", stats["eligible"])
    print("Repeated name+brand groups:", stats["duplicate_identity_groups"])
    print("Rows in repeated name+brand groups:", stats["duplicate_identity_rows"])
    print("Rows requiring admin review:", stats["review_required_rows"])
    print("Rows with malformed numeric cells:", stats["rows_with_malformed_numeric_values"])
    print("Rows with <threshold nutrient values:", stats["rows_with_less_than_values"])
    print("Rows with empty/all-zero nutrition:", stats["rows_with_empty_or_zero_nutrition"])
    print("CSV SHA-256:", stats["dataset_sha256"])
    print("Imported as current shop price/MRP: 0")
    print("Imported as retail pack size/net quantity/barcode: 0")
    print("Product images imported: 0")
    print("First eligible products:")
    for item in records[:8]:
        print("  -", item["name"], "|", item["brand"] or "brand not listed",
              "|", item["category"], "/", item["subcategory"])

    if not records:
        print("No eligible rows to import.")
        return 0
    if not args.apply:
        print("Dry run only. No API calls or database changes were made.")
        print("Apply remains blocked until the operator verifies licence scope and intended commercial use.")
        return 0

    if not args.license_confirmed or not args.commercial_use_confirmed:
        print("Apply blocked: both --license-confirmed and --commercial-use-confirmed are required.", file=sys.stderr)
        return 2
    if args.permission_reference.strip() != DOI:
        print(f"Apply blocked: permission reference must be dataset DOI {DOI}.", file=sys.stderr)
        return 2
    token = os.environ.get("APNIKO_ADMIN_TOKEN", "").strip()
    if not token:
        print("Set APNIKO_ADMIN_TOKEN to an active administrator JWT.", file=sys.stderr)
        return 2

    base = validate_api_url(args.api_url)
    attribution = (
        "Duddela Sai Prashanth, Ananya Devadiga, Saathwik Saathwik, Priya R Kamath "
        "and Rakshith Bhandary (2026), Indian Packaged Foods Nutritional Composition "
        "Dataset (2026), Version 1, Mendeley Data, DOI: 10.17632/rhswn9z9xr.1, "
        "CC BY 4.0. Apniko modifications: field validation, source-row preservation, "
        "duplicate review flags and product identity normalization."
    )
    client = session or requests.Session()
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    inserted = updated = unchanged = rejected = 0
    try:
        batches = [records[i:i + args.batch_size] for i in range(0, len(records), args.batch_size)]
        for index, batch in enumerate(batches, start=1):
            response = client.post(
                base + "/api/admin/catalogue/import",
                json={
                    "sourceName": SOURCE_NAME,
                    "sourceUrl": SOURCE_URL,
                    "sourceLicense": SOURCE_LICENSE,
                    "attribution": attribution,
                    "reuseRightsVerified": True,
                    "commercialUsePermitted": True,
                    "permissionReference": DOI,
                    "products": batch,
                },
                headers=headers,
                timeout=(5, 60),
            )
            response.raise_for_status()
            result = response.json()
            inserted += int(result.get("inserted", 0))
            updated += int(result.get("updated", 0))
            unchanged += int(result.get("unchanged", 0))
            rejected += int(result.get("rejected", 0))
            print(
                f"Batch {index}/{len(batches)}: inserted={result.get('inserted', 0)}, "
                f"updated={result.get('updated', 0)}, unchanged={result.get('unchanged', 0)}, "
                f"rejected={result.get('rejected', 0)}"
            )
            for error in result.get("errors", []):
                print("  REJECTED:", error)
    finally:
        if session is None:
            client.close()

    print("Import summary:", json.dumps({
        "inserted": inserted, "updated": updated,
        "unchanged": unchanged, "rejected": rejected,
    }))
    return 0 if rejected == 0 else 3


def main() -> int:
    try:
        return run(parse_args())
    except (OSError, ValueError, requests.RequestException) as error:
        print("Mendeley catalogue import failed:", error, file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
