#!/usr/bin/env python3
"""Import an authorised BigBasket product CSV into Apniko.

The importer is file-based and never scrapes BigBasket or downloads product
images. Dry-run is the default. Applying records requires an administrator
token, an explicit permission confirmation, and a written permission reference.
Retailer prices, ratings, barcodes and image URLs are not inferred or imported.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import requests

SOURCE_NAME = "BigBasket authorised dataset"
SOURCE_LICENSE = "BIGBASKET-WRITTEN-PERMISSION"
DEFAULT_SOURCE_URL = (
    "https://www.kaggle.com/datasets/surajjha101/"
    "bigbasket-entire-product-list-28k-datapoints"
)
MAX_BATCH_SIZE = 500


def clean(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "").strip())


def pick(row: dict[str, str], *names: str) -> str:
    normalized = {str(key).strip().casefold(): value for key, value in row.items() if key}
    for name in names:
        value = clean(normalized.get(name.casefold()))
        if value:
            return value
    return ""


def stable_record_id(row: dict[str, str], row_number: int) -> str:
    supplied = pick(row, "index", "id", "product_id", "productid", "sku")
    if supplied:
        return "bigbasket:" + supplied[:140]
    identity = "|".join((
        pick(row, "product", "productname", "product_name", "name").casefold(),
        pick(row, "brand").casefold(),
        pick(row, "category").casefold(),
        pick(row, "sub_category", "subcategory").casefold(),
        pick(row, "type", "variant").casefold(),
    ))
    digest = hashlib.sha256(identity.encode("utf-8")).hexdigest()
    return "bigbasket:sha256:" + digest


def normalize_row(row: dict[str, str], row_number: int) -> dict | None:
    name = pick(row, "product", "productname", "product_name", "name")
    if not name:
        return None

    category = pick(row, "category") or "Unclassified"
    subcategory = pick(row, "sub_category", "subcategory", "sub category") or "Unclassified"
    brand = pick(row, "brand") or None
    variant = pick(row, "type", "variant") or None
    description = pick(row, "description", "product_description") or None

    # The widely shared BigBasket Entire Product List schema has no verified
    # GTIN or image asset. Pack size is not parsed from free-text product names.
    # sale_price, market_price and rating are deliberately excluded: they are
    # retailer-specific historical values, not this shop's price or verified MRP.
    return {
        "name": name[:120],
        "brand": brand[:100] if brand else None,
        "barcode": None,
        "packSize": None,
        "netQuantity": None,
        "netQuantityUnit": None,
        "variantGroupKey": variant[:120] if variant else None,
        "description": description[:1000] if description else None,
        "category": category[:80],
        "subcategory": subcategory[:100],
        "mrp": None,
        "mrpSource": None,
        "sourceRecordId": stable_record_id(row, row_number),
        "sourceModifiedAt": None,
    }


def load_bigbasket_csv(path: Path, limit: int = 0) -> tuple[list[dict], dict[str, int]]:
    if not path.is_file():
        raise ValueError(f"CSV file not found: {path}")
    records: list[dict] = []
    seen: set[str] = set()
    received = skipped = duplicates = 0
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = {str(item or "").strip().casefold() for item in (reader.fieldnames or [])}
        if not headers.intersection({"product", "productname", "product_name", "name"}):
            raise ValueError("Expected BigBasket CSV column 'product' (or product_name/name).")
        for row_number, row in enumerate(reader, start=2):
            if limit and len(records) >= limit:
                break
            received += 1
            item = normalize_row(row, row_number)
            if item is None:
                skipped += 1
                continue
            record_id = item["sourceRecordId"]
            if record_id in seen:
                duplicates += 1
                continue
            seen.add(record_id)
            records.append(item)
    return records, {
        "received": received,
        "eligible": len(records),
        "skipped_missing_name": skipped,
        "duplicate_source_rows": duplicates,
    }


def validate_api_url(value: str) -> str:
    from urllib.parse import urlsplit
    parsed = urlsplit(value)
    host = (parsed.hostname or "").lower()
    local = host in {"localhost", "127.0.0.1", "::1"}
    if parsed.scheme != "https" and not (parsed.scheme == "http" and local):
        raise ValueError("Apniko API must use HTTPS, or HTTP on localhost only.")
    if not host or parsed.username or parsed.password:
        raise ValueError("Apniko API URL is invalid.")
    return value.rstrip("/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="Locally held BigBasket CSV; never downloaded by this script")
    parser.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    parser.add_argument("--source-url", default=DEFAULT_SOURCE_URL)
    parser.add_argument("--permission-reference", help="Written BigBasket/data-provider permission or licence reference")
    parser.add_argument("--permission-confirmed", action="store_true",
                        help="Confirm that written permission covers this dataset's reuse in Apniko")
    parser.add_argument("--limit", type=int, default=0, help="Optional row limit; 0 imports all eligible rows")
    parser.add_argument("--batch-size", type=int, default=250)
    parser.add_argument("--apply", action="store_true", help="Submit validated records to the local admin import API")
    return parser.parse_args()


def run(args: argparse.Namespace, session: requests.Session | None = None) -> int:
    if args.limit < 0 or not 1 <= args.batch_size <= MAX_BATCH_SIZE:
        print("Use --limit >= 0 and --batch-size 1..500.", file=sys.stderr)
        return 2
    records, stats = load_bigbasket_csv(args.file, args.limit)
    print("Source:", SOURCE_NAME)
    print("Mode:", "APPLY" if args.apply else "DRY RUN")
    print("CSV rows read:", stats["received"])
    print("Eligible product rows:", stats["eligible"])
    print("Skipped missing product names:", stats["skipped_missing_name"])
    print("Duplicate source rows:", stats["duplicate_source_rows"])
    print("Barcode imported: 0 (not available/verified in this dataset schema)")
    print("Images imported: 0 (BigBasket dataset file has no authorised image feed)")
    print("Retailer sale_price / market_price / rating imported: 0")
    print("First eligible products:")
    for item in records[:8]:
        print("  -", item["name"], "|", item["brand"] or "brand not listed",
              "|", item["category"], "/", item["subcategory"])

    if not records:
        print("No eligible rows to import.")
        return 0
    if not args.apply:
        print("Dry run only. No database or API changes were made.")
        if not args.permission_reference:
            print("Apply is locked until written dataset reuse permission is documented.")
        return 0

    if not args.permission_confirmed or not args.permission_reference or not args.permission_reference.strip():
        print(
            "Apply blocked: provide --permission-reference and --permission-confirmed "
            "only after confirming written permission for reuse in Apniko.",
            file=sys.stderr,
        )
        return 2
    token = os.environ.get("APNIKO_ADMIN_TOKEN", "").strip()
    if not token:
        print("Set APNIKO_ADMIN_TOKEN to an active administrator JWT.", file=sys.stderr)
        return 2

    base = validate_api_url(args.api_url)
    source_url = args.source_url.strip()
    if not source_url.startswith("https://"):
        print("Dataset source URL must use HTTPS.", file=sys.stderr)
        return 2
    attribution = (
        "BigBasket dataset fields: product, category, sub_category, brand, type, description. "
        "Retailer prices/ratings and images were excluded. "
        "Permission reference: " + args.permission_reference.strip()
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
                    "sourceUrl": source_url,
                    "sourceLicense": SOURCE_LICENSE,
                    "attribution": attribution,
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
                f"Batch {index}/{len(batches)}: "
                f"inserted={result.get('inserted', 0)}, "
                f"updated={result.get('updated', 0)}, "
                f"unchanged={result.get('unchanged', 0)}, "
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
        print("BigBasket catalogue import failed:", error, file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
