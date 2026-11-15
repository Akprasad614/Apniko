#!/usr/bin/env python3
"""Download, validate, deduplicate and import licensed India-first catalogue data."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import time
import requests
from urllib.parse import urljoin, urlsplit
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable

PACKSENSE_URL = (
    "https://huggingface.co/datasets/PackSense/packsense-india/"
    "resolve/main/india_products.csv"
)
PACKSENSE_SHA256 = (
    "9007b7a48fe9bc129e6a90a6fc7dfb4dfdcb6a72f53b3a3aabc74eda0d5d1951"
)
PACKSENSE_ROWS = 25209
PACKSENSE_SOURCE_URL = "https://huggingface.co/datasets/PackSense/packsense-india"
OPENMRP_API = "https://api.openmrp.in/v1/dump"
OPENMRP_SOURCE_URL = "https://openmrp.in/downloads"
OFFICIAL_MRP_SOURCES = {"pack", "brand", "gov", "off"}
BATCH_SIZE_LIMIT = 500
SOURCE_HOSTS = {"huggingface.co", "api.openmrp.in", "openmrp.in"}
REDIRECT_STATUSES = {301, 302, 303, 307, 308}


def validate_source_url(url: str) -> None:
    parsed = urlsplit(url)
    host = (parsed.hostname or "").lower()
    allowed_host = (
        host in SOURCE_HOSTS
        or host.endswith(".hf.co")
        or host.endswith(".openmrp.in")
    )
    if (
        parsed.scheme.lower() != "https"
        or not host
        or not allowed_host
        or parsed.username is not None
        or parsed.password is not None
        or (parsed.port is not None and parsed.port != 443)
    ):
        raise ValueError("Source requests must use HTTPS to a configured catalogue host")


def safe_source_get(url: str, timeout: tuple[int, int] = (10, 60)):
    current = url
    for _ in range(6):
        validate_source_url(current)
        response = requests.get(
            current,
            headers={"User-Agent": "ApnikoCatalogueImporter/1.0"},
            timeout=timeout,
            stream=True,
            allow_redirects=False,
        )
        if response.status_code in REDIRECT_STATUSES:
            location = response.headers.get("Location")
            response.close()
            if not location:
                raise ValueError("Catalogue source returned a redirect without a location")
            current = urljoin(current, location)
            continue
        return response
    raise ValueError("Catalogue source exceeded the allowed redirect limit")


def validate_api_base_url(base_url: str) -> str:
    parsed = urlsplit(base_url)
    host = (parsed.hostname or "").lower()
    local_http_hosts = {"localhost", "127.0.0.1", "::1"}
    if (
        parsed.scheme.lower() not in {"http", "https"}
        or not host
        or parsed.username is not None
        or parsed.password is not None
        or (parsed.scheme.lower() == "http" and host not in local_http_hosts)
    ):
        raise ValueError("Apniko API must use HTTPS, or HTTP on localhost only")
    return base_url.rstrip("/")


def valid_gtin(value: str | None) -> bool:
    """Return true for a valid GTIN-8/12/13/14 including its check digit."""
    if not value:
        return False
    code = re.sub(r"\s+", "", value)
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code):
        return False
    total = 0
    weight = 3
    for character in reversed(code[:-1]):
        total += int(character) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def clean(value: object) -> str:
    return str(value or "").strip()


def decimal_value(value: object) -> Decimal | None:
    text = clean(value).replace(",", "")
    if not text:
        return None
    try:
        parsed = Decimal(text)
    except InvalidOperation:
        return None
    return parsed if parsed.is_finite() and parsed > 0 else None


def unit_value(value: object) -> str | None:
    unit = clean(value).lower().rstrip(".")
    mapping = {
        "gram": "g", "grams": "g", "gm": "g", "g": "g",
        "kilogram": "kg", "kilograms": "kg", "kg": "kg",
        "millilitre": "ml", "milliliter": "ml", "millilitres": "ml",
        "milliliters": "ml", "ml": "ml",
        "litre": "l", "liter": "l", "litres": "l", "liters": "l", "l": "l",
        "piece": "pcs", "pieces": "pcs", "pcs": "pcs", "unit": "unit",
    }
    return mapping.get(unit)


def pack_size(amount: object, unit: object) -> str | None:
    quantity = decimal_value(amount)
    normalized_unit = unit_value(unit)
    if quantity is None or normalized_unit is None:
        return None
    amount_text = format(quantity.normalize(), "f")
    return f"{amount_text} {normalized_unit}"


def api_quantity(amount: object, unit: object) -> tuple[float | None, str | None]:
    """Return a DB-safe numeric quantity while retaining full packSize text."""
    quantity = decimal_value(amount)
    normalized_unit = unit_value(unit) if quantity is not None else None
    if quantity is None or normalized_unit is None:
        return None, None

    normalized_quantity = quantity.normalize()
    decimal_places = max(0, -normalized_quantity.as_tuple().exponent)
    if quantity > Decimal("1000000") or decimal_places > 3:
        return None, None

    return float(normalized_quantity), normalized_unit


def category_for(product_name: object, *taxonomy_values: object) -> tuple[str, str]:
    """Prefer explicit product identity over broad taxonomy or flavour keywords."""
    name = clean(product_name).lower().replace("-", " ")
    name = re.sub(r"[^a-z0-9 ]+", " ", name)
    name = re.sub(r"\s+", " ", name).strip()

    # Product-name rules avoid broad taxonomy terms (for example, "fruit"
    # in a croissant flavour or "vegetable" in vegetable oil) winning first.
    name_checks = [
        (("poha", "flattened rice", "beaten rice", "rice flakes"), "Foodgrains, Oil & Masala", "Rice & Rice Products"),
        (("mustard oil", "sunflower oil", "groundnut oil", "rice bran oil",
          "coconut oil", "olive oil", "soybean oil", "edible oil", "cooking oil",
          "vegetable oil", "palmolein", "kachi ghani", "ghee"),
         "Foodgrains, Oil & Masala", "Edible Oils & Ghee"),
        (("croissant", "pastry", "rusk", "bun", "pav", "bread", "bakery", "cake"),
         "Dairy, Bakery & Eggs", "Bread & Bakery"),
        (("hair oil", "massage oil"), "Beauty & Hygiene", "Hair Care"),
    ]
    for words, category, subcategory in name_checks:
        if any(word in name for word in words):
            return category, subcategory

    taxonomy_text = " ".join(
        clean(value).lower().replace("-", " ") for value in taxonomy_values
    )
    taxonomy_text = re.sub(r"[^a-z0-9 ]+", " ", taxonomy_text)
    all_text = f"{taxonomy_text} {name}".strip()

    checks = [
        (("baby", "diaper", "nappy", "infant"), "Baby Care", "Diapers & Wipes"),
        (("pet food", "dog food", "cat food", "pet care"), "Kitchen, Garden & Pets", "Pet Food & Supplies"),
        (("leafy", "spinach", "lettuce", "methi", "greens"), "Fruits & Vegetables", "Leafy Greens"),
        (("laundry", "detergent", "dishwash", "cleaner", "cleaning", "household"), "Cleaning & Household", "Home & Surface Cleaning"),
        (("shampoo", "skin care", "skincare", "cosmetic", "oral care", "toothpaste", "hygiene"), "Beauty & Hygiene", "Personal Hygiene"),
        (("coffee", "tea", "cocoa"), "Beverages", "Tea, Coffee & Cocoa"),
        (("milk", "dairy", "cheese", "yogurt", "yoghurt", "curd", "butter"), "Dairy, Bakery & Eggs", "Milk & Dairy"),
        (("bread", "bakery", "cake"), "Dairy, Bakery & Eggs", "Bread & Bakery"),
        (("egg", "eggs"), "Dairy, Bakery & Eggs", "Eggs"),
        (("fish", "seafood"), "Eggs, Meat & Fish", "Fish & Seafood"),
        (("meat", "poultry", "chicken"), "Eggs, Meat & Fish", "Poultry & Meat"),
        (("chocolate", "candy", "confectionery", "sweet"), "Snacks & Branded Foods", "Chocolates & Confectionery"),
        (("chips", "crisps", "namkeen", "salty snack"), "Snacks & Branded Foods", "Chips & Namkeen"),
        (("biscuit", "cookie"), "Snacks & Branded Foods", "Biscuits & Cookies"),
        (("noodle", "instant food"), "Snacks & Branded Foods", "Instant Foods & Noodles"),
        (("breakfast", "cereal"), "Snacks & Branded Foods", "Breakfast & Cereals"),
        (("oil", "ghee"), "Foodgrains, Oil & Masala", "Edible Oils & Ghee"),
        (("spice", "masala"), "Foodgrains, Oil & Masala", "Spices & Masalas"),
        (("pulse", "lentil", "dal"), "Foodgrains, Oil & Masala", "Pulses & Lentils"),
        (("atta", "flour"), "Foodgrains, Oil & Masala", "Atta & Flours"),
        (("rice",), "Foodgrains, Oil & Masala", "Rice & Rice Products"),
        (("salt", "sugar"), "Foodgrains, Oil & Masala", "Salt & Sugar"),
        (("sauce", "spread", "condiment", "jam", "honey", "syrup"), "Gourmet & World Food", "Sauces, Spreads & Condiments"),
        (("vegetable", "vegetables", "sabzi", "produce"), "Fruits & Vegetables", "Fresh Vegetables"),
        (("fruit", "fruits"), "Fruits & Vegetables", "Fresh Fruits"),
        (("beverage", "drink", "juice", "water", "soda", "soft drink"), "Beverages", "Water, Juices & Soft Drinks"),
        (("kitchen", "garden", "homeware"), "Kitchen, Garden & Pets", "Kitchen Essentials"),
    ]

    # Source taxonomy is preferred; the product name is a fallback only.
    for text in (taxonomy_text, all_text):
        if not text:
            continue
        for words, category, subcategory in checks:
            if any(word in text for word in words):
                return category, subcategory
    return "Other Essentials", "Unclassified Packaged Goods"


def is_non_grocery_or_high_risk(*values: object) -> bool:
    text = " ".join(clean(value).lower() for value in values)
    return any(term in text for term in (
        "medicine", "medicines", "pharmaceutical", "drug product",
        "mobile phone", "smartphone", "electronics", "electronic device",
    ))


def parse_unix_time(value: object) -> str | None:
    text = clean(value)
    if not text.isdigit():
        return None
    from datetime import datetime, timezone
    try:
        return datetime.fromtimestamp(int(text), tz=timezone.utc).isoformat().replace("+00:00", "Z")
    except (OverflowError, OSError, ValueError):
        return None


def packsense_item(row: dict[str, str]) -> dict | None:
    code = clean(row.get("code"))
    name = clean(row.get("product_name"))
    status = clean(row.get("existence_status")).lower()
    error_flags = clean(row.get("open_error_flags")) or "0"

    if status not in {"present_in_snapshot", "confirmed_exists"}:
        return None
    if not name or not valid_gtin(code):
        return None
    try:
        if int(error_flags) > 0:
            return None
    except ValueError:
        return None
    if is_non_grocery_or_high_risk(
        name, row.get("pnns_groups_1"), row.get("pnns_groups_2"),
        row.get("categories_tags"),
    ):
        return None

    amount = (row.get("product_quantity") or row.get("quantity_amount") or "").strip()
    unit = (row.get("product_quantity_unit") or row.get("quantity_unit") or "").strip()
    size = pack_size(amount, unit)
    net_quantity, normalized_unit = api_quantity(amount, unit)
    category, subcategory = category_for(
        name, row.get("pnns_groups_1"), row.get("pnns_groups_2"),
        row.get("categories_tags"),
    )
    brand = clean(row.get("brands"))
    if brand:
        brand = brand.split(",")[0].strip()

    return {
        "name": name[:120],
        "brand": brand[:100] or None,
        "barcode": code,
        "packSize": size,
        "netQuantity": net_quantity,
        "netQuantityUnit": normalized_unit,
        "variantGroupKey": None,
        "description": None,
        "category": category,
        "subcategory": subcategory,
        "mrp": None,
        "mrpSource": None,
        "sourceRecordId": code,
        "sourceModifiedAt": parse_unix_time(row.get("last_modified_t")),
    }


def public_status(value: object) -> bool:
    status = clean(value).lower()
    return not status or status in {"approved", "published", "active", "verified"}


def openmrp_items(products_file: Path, brands_file: Path, variants_file: Path) -> Iterable[dict]:
    with brands_file.open(encoding="utf-8-sig", newline="") as handle:
        brands = {clean(row.get("id")): clean(row.get("name")) for row in csv.DictReader(handle)}

    with products_file.open(encoding="utf-8-sig", newline="") as handle:
        products = {
            clean(row.get("id")): row
            for row in csv.DictReader(handle)
            if clean(row.get("id")) and public_status(row.get("moderation_status"))
        }

    with variants_file.open(encoding="utf-8-sig", newline="") as handle:
        for variant in csv.DictReader(handle):
            product = products.get(clean(variant.get("product_id")))
            if not product or not public_status(variant.get("moderation_status")):
                continue
            barcode = clean(variant.get("barcode")) or None
            if barcode and not valid_gtin(barcode):
                continue

            size = pack_size(variant.get("pack_size"), variant.get("unit"))
            net_quantity, normalized_unit = api_quantity(
                variant.get("pack_size"), variant.get("unit")
            )
            mrp = None
            mrp_source = clean(variant.get("mrp_source")).lower() or None
            mrp_paise = decimal_value(variant.get("mrp_paise"))
            if mrp_paise is not None and mrp_source in OFFICIAL_MRP_SOURCES:
                mrp = float(mrp_paise / Decimal("100"))
            else:
                mrp_source = None

            category, subcategory = category_for(product.get("name"), product.get("category"))
            brand = brands.get(clean(product.get("brand_id"))) or None
            product_id = clean(product.get("id"))
            variant_id = clean(variant.get("id"))
            yield {
                "name": clean(product.get("name"))[:120],
                "brand": brand[:100] if brand else None,
                "barcode": barcode,
                "packSize": size or clean(variant.get("label"))[:80] or None,
                "netQuantity": net_quantity,
                "netQuantityUnit": normalized_unit,
                "variantGroupKey": clean(product.get("group_key"))[:120] or None,
                "description": clean(product.get("description"))[:1000] or None,
                "category": category,
                "subcategory": subcategory,
                "mrp": mrp,
                "mrpSource": mrp_source,
                "sourceRecordId": f"{product_id}:{variant_id}",
                "sourceModifiedAt": None,
            }


def download_file(url: str, destination: Path, expected_sha256: str | None = None) -> str:
    destination.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    temporary = destination.with_suffix(destination.suffix + ".part")
    response = None
    try:
        response = safe_source_get(url, timeout=(10, 90))
        response.raise_for_status()
        with temporary.open("wb") as output:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    digest.update(chunk)
                    output.write(chunk)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise
    finally:
        if response is not None:
            response.close()
    actual = digest.hexdigest()
    if expected_sha256 and actual.lower() != expected_sha256.lower():
        temporary.unlink(missing_ok=True)
        raise ValueError(
            f"SHA-256 mismatch for {destination.name}: expected {expected_sha256}, got {actual}"
        )
    temporary.replace(destination)
    return actual


def download_packsense(output_dir: Path) -> Path:
    output = output_dir / "packsense-india.csv"
    digest = download_file(PACKSENSE_URL, output, PACKSENSE_SHA256)
    with output.open(encoding="utf-8-sig", newline="") as handle:
        rows = sum(1 for _ in csv.DictReader(handle))
    if rows != PACKSENSE_ROWS:
        raise ValueError(f"PackSense snapshot row count changed: expected {PACKSENSE_ROWS}, got {rows}")
    print(f"Downloaded PackSense India: {rows:,} source rows; SHA-256 {digest}")
    return output


def download_openmrp(output_dir: Path) -> dict[str, Path]:
    with safe_source_get(f"{OPENMRP_API}/manifest", timeout=(10, 30)) as response:
        response.raise_for_status()
        manifest = response.json()
    manifest_path = output_dir / "openmrp-manifest.json"
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    downloaded: dict[str, Path] = {}
    for item in manifest.get("files", []):
        table = clean(item.get("table")).lower()
        file_format = clean(item.get("format")).lower()
        filename = clean(item.get("name") or item.get("path"))
        if not filename and table in {"brands", "products", "variants"} and file_format == "csv":
            filename = f"{table}.csv"
        if not filename.endswith(".csv") or table not in {"brands", "products", "variants"}:
            continue
        expected = clean(item.get("sha256")) or None
        destination = output_dir / f"{table}.csv"
        url = f"{OPENMRP_API}/file/{filename}"
        digest = download_file(url, destination, expected)
        downloaded[table] = destination
        print(f"Downloaded OpenMRP {table}: {item.get('rows', '?')} rows; SHA-256 {digest}")

    missing = {"brands", "products", "variants"} - downloaded.keys()
    if missing:
        raise ValueError(f"OpenMRP manifest did not provide required CSV files: {', '.join(sorted(missing))}")
    return downloaded


def send_batch(base_url: str, token: str, source: str, batch: list[dict], timeout: int = 120) -> dict:
    if not batch:
        return {}
    if len(batch) > BATCH_SIZE_LIMIT:
        raise ValueError(f"Batch exceeds {BATCH_SIZE_LIMIT} rows")
    if source == "packsense":
        metadata = {
            "sourceName": "PackSense India / Open Food Facts",
            "sourceUrl": PACKSENSE_SOURCE_URL,
            "sourceLicense": "ODbL-1.0",
            "attribution": (
                "PackSense India product catalogue, built 2026-11-03 from Open Food Facts; "
                "ODbL-1.0 database, individual contents DbCL-1.0."
            ),
        }
    else:
        metadata = {
            "sourceName": "OpenMRP",
            "sourceUrl": OPENMRP_SOURCE_URL,
            "sourceLicense": "ODbL-1.0",
            "attribution": "OpenMRP India product database; ODbL-1.0. Product media is not imported.",
        }

    payload = {**metadata, "products": batch}
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    api_root = validate_api_base_url(base_url)
    response = requests.post(
        f"{api_root}/api/admin/catalogue/import",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "ApnikoCatalogueImporter/1.0",
        },
        timeout=timeout,
        allow_redirects=False,
    )
    try:
        response.raise_for_status()
    except requests.HTTPError as error:
        detail = response.text[:2000]
        raise RuntimeError(
            f"Catalogue API returned HTTP {response.status_code}: {detail}"
        ) from error
    if 300 <= response.status_code < 400:
        raise RuntimeError("Catalogue API redirects are not allowed for authenticated imports")
    return response.json()


def load_packSense(file_path: Path, limit: int | None = None) -> tuple[list[dict], dict]:
    result = []
    counters = defaultdict(int)
    seen = set()
    with file_path.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            counters["source_rows"] += 1
            item = packsense_item(row)
            if item is None:
                counters["excluded"] += 1
                continue
            key = item["barcode"]
            if key in seen:
                counters["duplicate_rows"] += 1
                continue
            seen.add(key)
            result.append(item)
            if limit and len(result) >= limit:
                break
    return result, dict(counters)


def load_openmrp(products: Path, brands: Path, variants: Path,
                 limit: int | None = None) -> tuple[list[dict], dict]:
    result = []
    seen = set()
    counters = defaultdict(int)
    for item in openmrp_items(products, brands, variants):
        counters["eligible_variants"] += 1
        identity = item["barcode"] or item["sourceRecordId"]
        if identity in seen:
            counters["duplicate_rows"] += 1
            continue
        seen.add(identity)
        result.append(item)
        if limit and len(result) >= limit:
            break
    counters["excluded_or_unapproved"] = counters["eligible_variants"] - len(result) - counters["duplicate_rows"]
    return result, dict(counters)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    download_parser = subparsers.add_parser("download", help="Download a pinned India-first data snapshot")
    download_parser.add_argument("--source", choices=("packsense", "openmrp"), required=True)
    download_parser.add_argument("--output-dir", type=Path, default=Path("data/catalogue/source"))

    import_parser = subparsers.add_parser("import", help="Validate and optionally import a CSV snapshot")
    import_parser.add_argument("--source", choices=("packsense", "openmrp"), required=True)
    import_parser.add_argument("--file", type=Path, help="PackSense india_products.csv")
    import_parser.add_argument("--products", type=Path, help="OpenMRP products.csv")
    import_parser.add_argument("--brands", type=Path, help="OpenMRP brands.csv")
    import_parser.add_argument("--variants", type=Path, help="OpenMRP variants.csv")
    import_parser.add_argument("--base-url", default="http://localhost:8080")
    import_parser.add_argument("--token", default=None, help="Admin JWT; APNIKO_ADMIN_TOKEN is preferred")
    import_parser.add_argument("--batch-size", type=int, default=250)
    import_parser.add_argument("--limit", type=int, default=None)
    import_parser.add_argument("--apply", action="store_true", help="Send validated records to the admin API")

    args = parser.parse_args()
    if args.command == "download":
        args.output_dir.mkdir(parents=True, exist_ok=True)
        if args.source == "packsense":
            download_packsense(args.output_dir)
        else:
            download_openmrp(args.output_dir)
        return 0

    if not 1 <= args.batch_size <= BATCH_SIZE_LIMIT:
        parser.error(f"--batch-size must be between 1 and {BATCH_SIZE_LIMIT}")

    if args.source == "packsense":
        if not args.file:
            parser.error("--file is required for PackSense")
        products, counters = load_packSense(args.file, args.limit)
    else:
        if not all((args.products, args.brands, args.variants)):
            parser.error("--products, --brands and --variants are required for OpenMRP")
        products, counters = load_openmrp(args.products, args.brands, args.variants, args.limit)

    print(f"Eligible products: {len(products):,}")
    print(f"Source checks: {json.dumps(counters, ensure_ascii=False)}")
    if not args.apply:
        print("Dry run only. No database records were changed. Re-run with --apply to import.")
        return 0

    token = args.token or __import__("os").environ.get("APNIKO_ADMIN_TOKEN")
    if not token:
        parser.error("Set APNIKO_ADMIN_TOKEN or pass --token when using --apply")

    totals = defaultdict(int)
    batch_size = args.batch_size
    for start in range(0, len(products), batch_size):
        response = send_batch(args.base_url, token, args.source, products[start:start + batch_size])
        for key in ("received", "inserted", "updated", "unchanged", "rejected"):
            totals[key] += int(response.get(key, 0))
        print(
            f"Batch {start // batch_size + 1}: "
            f"+{response.get('inserted', 0)} new, "
            f"{response.get('updated', 0)} updated, "
            f"{response.get('unchanged', 0)} unchanged, "
            f"{response.get('rejected', 0)} rejected"
        )
        for error in response.get("errors", []):
            print(f"  {error}", file=sys.stderr)
        time.sleep(0.05)

    print("Import totals:", json.dumps(dict(totals), ensure_ascii=False))
    return 1 if totals["rejected"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
