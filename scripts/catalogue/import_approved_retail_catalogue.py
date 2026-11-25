#!/usr/bin/env python3
"""Dry-run-first CSV intake for a separately approved commercial retail catalogue.

This tool never downloads or scrapes a retailer. An operator must configure the
same source approval in the backend after independently reviewing its rights.
Every accepted retail row enters NEEDS_REVIEW and is not customer-visible until
an administrator approves it in the moderation queue.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from decimal import Decimal, InvalidOperation
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit

import requests

CSV_FIELDS = (
    "source_record_id",
    "name",
    "brand",
    "barcode",
    "pack_size",
    "net_quantity",
    "net_quantity_unit",
    "variant_group_key",
    "description",
    "category",
    "subcategory",
    "mrp",
    "mrp_source",
    "source_modified_at",
)
REQUIRED_FIELDS = (
    "source_record_id", "name", "barcode", "pack_size",
    "net_quantity", "net_quantity_unit", "category", "subcategory",
)
VALID_UNITS = {"g", "kg", "ml", "l", "pcs", "unit"}
VALID_MRP_SOURCES = {"pack", "brand", "gov"}
SOURCE_ID = re.compile(r"^[^\s]{1,160}$")


def clean(value: object) -> str:
    return "" if value is None else re.sub(r"\s+", " ", str(value)).strip()


def valid_gtin(value: str) -> bool:
    barcode = clean(value).replace(" ", "")
    if not re.fullmatch(r"(?:[0-9]{8}|[0-9]{12,14})", barcode):
        return False
    total = 0
    weight = 3
    for digit in reversed(barcode[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(barcode[-1])


def positive_decimal(value: str, field: str, scale: int, maximum: str) -> Decimal:
    try:
        number = Decimal(clean(value))
    except (InvalidOperation, ValueError):
        raise ValueError(f"{field} must be a valid decimal number") from None
    if not number.is_finite() or number <= 0 or number > Decimal(maximum):
        raise ValueError(f"{field} must be greater than zero and no more than {maximum}")
    if max(0, -number.as_tuple().exponent) > scale:
        raise ValueError(f"{field} cannot have more than {scale} decimal places")
    return number


def load_csv(path: Path) -> tuple[list[dict], dict]:
    if not path.is_file():
        raise ValueError(f"CSV file not found: {path}")

    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = reader.fieldnames or []
        if len(headers) != len(set(headers)):
            raise ValueError("CSV contains duplicate column names")
        missing = sorted(set(CSV_FIELDS) - set(headers))
        extra = sorted(set(headers) - set(CSV_FIELDS))
        if missing or extra:
            raise ValueError(
                "CSV schema mismatch. "
                f"Missing columns: {missing or 'none'}; unexpected columns: {extra or 'none'}. "
                "Image, photo and URL columns are not accepted."
            )

        rows = []
        source_ids = set()
        for line_number, raw in enumerate(reader, start=2):
            if None in raw:
                raise ValueError(f"CSV row {line_number} contains extra unmapped columns")
            row = {field: clean(raw.get(field)) for field in CSV_FIELDS}
            for field in REQUIRED_FIELDS:
                if not row[field]:
                    raise ValueError(f"CSV row {line_number} is missing {field}")
            if not SOURCE_ID.fullmatch(row["source_record_id"]):
                raise ValueError(f"CSV row {line_number} has an invalid source_record_id")
            if row["source_record_id"] in source_ids:
                raise ValueError(f"CSV row {line_number} repeats source_record_id {row['source_record_id']!r}")
            source_ids.add(row["source_record_id"])
            if len(row["name"]) > 120 or len(row["category"]) > 80 or len(row["subcategory"]) > 100:
                raise ValueError(f"CSV row {line_number} exceeds Apniko's field length limits")
            if row["brand"] and len(row["brand"]) > 100:
                raise ValueError(f"CSV row {line_number} brand exceeds 100 characters")
            if row["pack_size"] and len(row["pack_size"]) > 80:
                raise ValueError(f"CSV row {line_number} pack_size exceeds 80 characters")
            if row["variant_group_key"] and len(row["variant_group_key"]) > 120:
                raise ValueError(f"CSV row {line_number} variant_group_key exceeds 120 characters")
            if row["description"] and len(row["description"]) > 1000:
                raise ValueError(f"CSV row {line_number} description exceeds 1000 characters")
            if row["source_modified_at"]:
                try:
                    modified_at = datetime.fromisoformat(row["source_modified_at"].replace("Z", "+00:00"))
                except ValueError:
                    raise ValueError(f"CSV row {line_number} source_modified_at must be ISO-8601") from None
                if modified_at.tzinfo is None or modified_at.utcoffset() is None:
                    raise ValueError(f"CSV row {line_number} source_modified_at must include a timezone")
            if not valid_gtin(row["barcode"]):
                raise ValueError(f"CSV row {line_number} has an invalid GTIN/check digit")
            quantity = positive_decimal(row["net_quantity"], "net_quantity", 3, "1000000")
            unit = row["net_quantity_unit"].lower()
            if unit not in VALID_UNITS:
                raise ValueError(f"CSV row {line_number} net_quantity_unit must be one of {sorted(VALID_UNITS)}")
            row["net_quantity"] = quantity
            row["net_quantity_unit"] = unit
            if row["mrp"]:
                mrp = positive_decimal(row["mrp"], "mrp", 2, "1000000")
                if row["mrp_source"].lower() not in VALID_MRP_SOURCES:
                    raise ValueError(f"CSV row {line_number} mrp_source must be pack, brand or gov")
                row["mrp"] = mrp
                row["mrp_source"] = row["mrp_source"].lower()
            elif row["mrp_source"]:
                raise ValueError(f"CSV row {line_number} cannot set mrp_source without MRP")
            rows.append(row)

    if not rows:
        raise ValueError("CSV contains no product rows")

    products = []
    for row in rows:
        source_record_id = row["source_record_id"]
        original_values = {
            key: (str(value) if isinstance(value, Decimal) else value)
            for key, value in row.items()
        }
        metadata = json.dumps({
            "sourceRecordId": source_record_id,
            "sourceValues": original_values,
            "fieldHandling": {
                "retailIdentity": "Barcode, pack size and net quantity are supplied by the approved source and require admin verification",
                "mrp": "Imported only when explicitly present with a declared source",
                "publication": "Pending manual admin moderation",
            },
        }, ensure_ascii=False, separators=(",", ":"))
        products.append({
            "name": row["name"],
            "brand": row["brand"] or None,
            "barcode": row["barcode"],
            "packSize": row["pack_size"],
            "netQuantity": str(row["net_quantity"]),
            "netQuantityUnit": row["net_quantity_unit"],
            "variantGroupKey": row["variant_group_key"] or None,
            "description": row["description"] or None,
            "category": row["category"],
            "subcategory": row["subcategory"],
            "mrp": str(row["mrp"]) if row["mrp"] else None,
            "mrpSource": row["mrp_source"] or None,
            "sourceRecordId": source_record_id,
            "sourceModifiedAt": row["source_modified_at"] or None,
            "sourceMetadataJson": metadata,
            "identityDiscriminator": source_record_id,
            "sourceReviewReason": "Imported retail identity requires administrator verification before customer publication.",
        })

    return products, {
        "rows": len(products),
        "unique_source_record_ids": len(source_ids),
        "valid_gtin_rows": len(products),
        "pending_admin_review": len(products),
    }


def validate_source_url(value: str) -> str:
    parsed = urlsplit(value)
    host = (parsed.hostname or "").lower()
    local = host in {"localhost", "127.0.0.1", "::1"}
    if (parsed.scheme != "https" and not (parsed.scheme == "http" and local)) or not host:
        raise ValueError("Source/API URLs must use HTTPS (HTTP is allowed only for localhost API testing)")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("URL must not contain credentials, query parameters or a fragment")
    return value.rstrip("/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="Locally supplied CSV; the tool does not download data")
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--source-url", required=True, help="Exact approved HTTPS source URL")
    parser.add_argument("--license", required=True, dest="source_license")
    parser.add_argument("--permission-reference", required=True, help="Signed agreement, approval or licence reference")
    parser.add_argument("--attribution", required=True, help="Must identify source, licence, permission reference and Apniko modifications")
    parser.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--license-confirmed", action="store_true")
    parser.add_argument("--commercial-use-confirmed", action="store_true")
    parser.add_argument("--apply", action="store_true", help="Submit rows to the admin API; dry-run is the default")
    return parser.parse_args()


def run(args: argparse.Namespace, session: requests.Session | None = None) -> int:
    if not 1 <= args.batch_size <= 250:
        print("--batch-size must be between 1 and 250", file=sys.stderr)
        return 2
    if not args.source_name.strip() or not args.source_license.strip() or not args.permission_reference.strip():
        print("Source name, licence and permission reference are required", file=sys.stderr)
        return 2

    source_url = validate_source_url(args.source_url)
    attribution = args.attribution.strip()
    required_attribution = (
        args.source_name.lower(), args.source_license.lower(),
        args.permission_reference.lower(), "apniko modifications",
    )
    if any(token not in attribution.lower() for token in required_attribution):
        print("Attribution must identify the source, licence, permission reference and Apniko modifications", file=sys.stderr)
        return 2

    products, stats = load_csv(args.file)
    print("Source:", args.source_name)
    print("Source URL:", source_url)
    print("Licence:", args.source_license)
    print("Rows:", stats["rows"])
    print("Valid GTIN rows:", stats["valid_gtin_rows"])
    print("Pending administrator review:", stats["pending_admin_review"])
    print("Product images imported: 0")
    print("Open Food Facts data imported: 0")
    print("Mode:", "APPLY" if args.apply else "DRY RUN")
    if not args.apply:
        print("Dry run only. No API calls or database changes were made.")
        print("Before applying, configure this exact source and verified rights in the backend environment.")
        return 0

    if not args.license_confirmed or not args.commercial_use_confirmed:
        print("Apply blocked: both --license-confirmed and --commercial-use-confirmed are required.", file=sys.stderr)
        return 2
    token = os.environ.get("APNIKO_ADMIN_TOKEN", "").strip()
    if not token:
        print("Set APNIKO_ADMIN_TOKEN to an active administrator JWT.", file=sys.stderr)
        return 2

    api_base = validate_source_url(args.api_url)
    client = session or requests.Session()
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    totals = {"inserted": 0, "updated": 0, "unchanged": 0, "rejected": 0}
    try:
        batches = [products[index:index + args.batch_size] for index in range(0, len(products), args.batch_size)]
        for index, batch in enumerate(batches, start=1):
            response = client.post(
                api_base + "/api/admin/catalogue/import",
                json={
                    "sourceName": args.source_name.strip(),
                    "sourceUrl": source_url,
                    "sourceLicense": args.source_license.strip(),
                    "attribution": attribution,
                    "reuseRightsVerified": True,
                    "commercialUsePermitted": True,
                    "permissionReference": args.permission_reference.strip(),
                    "products": batch,
                },
                headers=headers,
                timeout=(5, 60),
            )
            response.raise_for_status()
            result = response.json()
            for key in totals:
                totals[key] += int(result.get(key, 0))
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

    print("Import summary:", json.dumps(totals))
    return 0 if totals["rejected"] == 0 else 3


def main() -> int:
    try:
        return run(parse_args())
    except (OSError, ValueError, requests.RequestException) as error:
        print("Approved retail catalogue import failed:", error, file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
