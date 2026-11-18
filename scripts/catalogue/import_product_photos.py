#!/usr/bin/env python3
"""Dry-run-first, exact-GTIN product photo candidate importer for Apniko."""
from __future__ import annotations

import argparse
import os
import re
import sys
import time
import unicodedata
from decimal import Decimal, InvalidOperation
from urllib.parse import urlparse

import requests

API_URL = "https://world.openfoodfacts.org/api/v3/product/{barcode}.json"
LICENSE = "CC BY-SA"
SOURCES = {
    "world.openfoodfacts.org": "Open Food Facts",
    "world.openbeautyfacts.org": "Open Beauty Facts",
    "world.openpetfoodfacts.org": "Open Pet Food Facts",
    "world.openproductsfacts.org": "Open Products Facts",
}
IMAGE_HOSTS = {"images." + host.removeprefix("world.") for host in SOURCES}


def safe_image_url(value: str | None, barcode: str) -> bool:
    if not value:
        return False
    parsed = urlparse(value)
    if (parsed.scheme != "https" or parsed.hostname not in IMAGE_HOSTS
            or parsed.username or parsed.password or parsed.port not in (None, 443)
            or not parsed.path.startswith("/images/products/")
            or not re.sub(r"[^0-9]", "", parsed.path).startswith(barcode)
            or parsed.query or parsed.fragment):
        return False
    return True


PACK_SIZE_PATTERN = re.compile(
    r"(?:(?P<count>\d+(?:[.,]\d+)?)\s*[x×]\s*)?"
    r"(?P<amount>\d+(?:[.,]\d+)?)\s*"
    r"(?P<unit>milligrams?|mg|micrograms?|mcg|kilograms?|kg|grams?|gm|g|"
    r"milliliters?|millilitres?|ml|liters?|litres?|l|pieces?|pcs|counts?|ct)\b",
    re.IGNORECASE,
)

PACK_SIZE_UNITS = {
    "mg": ("mass", Decimal("0.001")), "milligram": ("mass", Decimal("0.001")),
    "milligrams": ("mass", Decimal("0.001")), "mcg": ("mass", Decimal("0.000001")),
    "microgram": ("mass", Decimal("0.000001")), "micrograms": ("mass", Decimal("0.000001")),
    "g": ("mass", Decimal("1")), "gm": ("mass", Decimal("1")),
    "gram": ("mass", Decimal("1")), "grams": ("mass", Decimal("1")),
    "kg": ("mass", Decimal("1000")), "kilogram": ("mass", Decimal("1000")),
    "kilograms": ("mass", Decimal("1000")),
    "ml": ("volume", Decimal("1")), "milliliter": ("volume", Decimal("1")),
    "milliliters": ("volume", Decimal("1")), "millilitre": ("volume", Decimal("1")),
    "millilitres": ("volume", Decimal("1")),
    "l": ("volume", Decimal("1000")), "liter": ("volume", Decimal("1000")),
    "liters": ("volume", Decimal("1000")), "litre": ("volume", Decimal("1000")),
    "litres": ("volume", Decimal("1000")),
    "pcs": ("count", Decimal("1")), "piece": ("count", Decimal("1")),
    "pieces": ("count", Decimal("1")), "count": ("count", Decimal("1")),
    "counts": ("count", Decimal("1")), "ct": ("count", Decimal("1")),
}


def normalize_brand(value: object) -> str:
    decomposed = unicodedata.normalize("NFKD", str(value or ""))
    without_marks = "".join(
        character for character in decomposed
        if not unicodedata.combining(character)
    )
    return "".join(character for character in without_marks.casefold() if character.isalnum())


def normalize_pack_size(value: object) -> tuple[str, Decimal] | None:
    match = PACK_SIZE_PATTERN.search(str(value or ""))
    if not match:
        return None
    unit = re.sub(r"[^a-z]", "", match.group("unit").casefold())
    unit_details = PACK_SIZE_UNITS.get(unit)
    if unit_details is None:
        return None
    try:
        amount = Decimal(match.group("amount").replace(",", "."))
        if match.group("count"):
            amount *= Decimal(match.group("count").replace(",", "."))
    except InvalidOperation:
        return None
    dimension, factor = unit_details
    return dimension, (amount * factor).normalize()


def same_brand(left: object, right: object) -> bool:
    normalized_left = normalize_brand(left)
    normalized_right = normalize_brand(right)
    return bool(normalized_left and normalized_right and normalized_left == normalized_right)


def same_pack_size(left: object, right: object) -> bool:
    normalized_left = normalize_pack_size(left)
    normalized_right = normalize_pack_size(right)
    if normalized_left is not None or normalized_right is not None:
        return normalized_left is not None and normalized_left == normalized_right
    exact_left = re.sub(r"\s+", " ", str(left or "").strip()).casefold()
    exact_right = re.sub(r"\s+", " ", str(right or "").strip()).casefold()
    return bool(exact_left and exact_right and exact_left == exact_right)


def explain_candidate(
    local_product: dict, payload: dict, api_url: str
) -> tuple[dict | None, str | None]:
    barcode = str(local_product.get("barcode") or "").strip()
    product = payload.get("product") or {}
    if not barcode:
        return None, "Apniko product has no barcode"
    if str(product.get("code") or "").strip() != barcode:
        return None, "source GTIN does not exactly match the Apniko barcode"

    source_host = urlparse(api_url).hostname
    source_name = SOURCES.get(source_host or "")
    if not source_name:
        return None, "source catalogue is not allowlisted"

    source_brand = str(product.get("brands") or "").split(",")[0].strip()
    source_pack_size = str(product.get("quantity") or "").strip()
    local_brand = str(local_product.get("brand") or "").strip()
    local_pack_size = str(local_product.get("packSize") or "").strip()
    # A GTIN match alone can still point to incomplete or inconsistent community data.
    # Do not offer a candidate if the source's brand or pack size is absent or different.
    if not source_brand:
        return None, "source record has no brand"
    if not local_brand:
        return None, "Apniko catalogue product has no brand to compare"
    if not same_brand(source_brand, local_brand):
        return None, f"brand mismatch (Apniko: {local_brand}; source: {source_brand})"
    if not source_pack_size:
        return None, "source record has no pack size"
    if not local_pack_size:
        return None, "Apniko catalogue product has no pack size to compare"
    if not same_pack_size(source_pack_size, local_pack_size):
        return None, f"pack-size mismatch (Apniko: {local_pack_size}; source: {source_pack_size})"

    source_product_name = str(product.get("product_name") or "").strip()
    if not source_product_name:
        return None, "source record has no product name"

    front = (product.get("selected_images") or {}).get("front") or {}
    variants = front.get("display") or front.get("small") or {}
    image_url = variants.get("en") or next(iter(variants.values()), None)
    if not image_url:
        return None, "source record has no front photograph"
    if not safe_image_url(image_url, barcode):
        return None, "front photograph URL is not on the matching allowlisted image host"

    photographers = product.get("photographers_tags") or []
    photographer_names = ", ".join(str(name) for name in photographers[:8] if str(name).strip())
    attribution = "Open Facts contributors"
    if photographer_names:
        attribution += "; photographers: " + photographer_names
    return {
        "imageUrl": image_url,
        "sourceUrl": f"https://{source_host}/product/{barcode}",
        "sourceName": source_name,
        "sourceProductName": source_product_name,
        "sourceProductBrand": source_brand,
        "sourcePackSize": source_pack_size,
        "license": LICENSE,
        "attribution": attribution[:500],
        "authorizationReference": None,
        "matchedBarcode": barcode,
    }, None


def find_candidate(local_product: dict, payload: dict, api_url: str) -> dict | None:
    candidate, _reason = explain_candidate(local_product, payload, api_url)
    return candidate


def parse_product_ids(value: str) -> set[int]:
    """Parse a comma-separated allowlist of positive Apniko product IDs."""
    raw_values = [part.strip() for part in value.split(",")]
    if not raw_values or any(not part for part in raw_values):
        raise argparse.ArgumentTypeError("Product IDs must be comma-separated positive integers")
    try:
        parsed = [int(part) for part in raw_values]
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "Product IDs must be comma-separated positive integers"
        ) from error
    if any(product_id <= 0 for product_id in parsed):
        raise argparse.ArgumentTypeError("Product IDs must be positive integers")
    if len(parsed) != len(set(parsed)):
        raise argparse.ArgumentTypeError("Product IDs must not contain duplicates")
    return set(parsed)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    result.add_argument("--limit", type=int, default=10)
    result.add_argument("--page", type=int, default=0)
    result.add_argument("--delay-seconds", type=float, default=4.1)
    result.add_argument(
        "--product-ids",
        type=parse_product_ids,
        help="Only inspect/submit these comma-separated Apniko product IDs",
    )
    result.add_argument("--apply", action="store_true", help="Submit matching candidates to the admin review queue")
    return result


def run(args: argparse.Namespace, session: requests.Session | None = None) -> int:
    token = os.environ.get("APNIKO_ADMIN_TOKEN", "").strip()
    if not token:
        print("Set APNIKO_ADMIN_TOKEN to an active administrator JWT.", file=sys.stderr)
        return 2
    if not 1 <= args.limit <= 100 or args.page < 0 or args.delay_seconds < 4.0:
        print("Use --limit 1..100, non-negative --page and --delay-seconds >= 4.", file=sys.stderr)
        return 2

    client = session or requests.Session()
    base = args.api_url.rstrip("/")
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    user_agent = os.environ.get(
        "APNIKO_OFF_USER_AGENT",
        "ApnikoProductPhotoImporter/1.0 (https://github.com/Akprasad614/Apniko)",
    )
    print("Mode:", "APPLY" if args.apply else "DRY RUN")
    products: list[dict] = []
    page = args.page
    while len(products) < args.limit:
        response = client.get(
            base + "/api/admin/catalogue/photo-import-targets",
            params={"page": page, "size": min(100, args.limit)},
            headers=headers,
            timeout=(5, 20),
        )
        response.raise_for_status()
        body = response.json()
        products.extend(body.get("content") or [])
        if body.get("last", True) or not body.get("content"):
            break
        page += 1
    products = products[:args.limit]
    requested_product_ids = getattr(args, "product_ids", None)
    if requested_product_ids:
        target_ids = {
            int(item["productId"])
            for item in products
            if item.get("productId") is not None
        }
        missing_ids = requested_product_ids - target_ids
        if missing_ids:
            missing_text = ", ".join(str(value) for value in sorted(missing_ids))
            print(
                "Requested product IDs are not in the fetched import-target page: "
                + missing_text
                + ". Increase --limit or select the correct --page.",
                file=sys.stderr,
            )
            return 2
        products = [
            item for item in products
            if int(item.get("productId", -1)) in requested_product_ids
        ]
        print("Product ID allowlist:", ", ".join(str(value) for value in sorted(requested_product_ids)))
    if not products:
        print("No barcode-backed products are waiting for a photo lookup.")
        return 0

    inspected = candidates = submitted = 0
    for item in products:
        barcode = str(item.get("barcode") or "").strip()
        if not barcode:
            continue
        if inspected:
            time.sleep(args.delay_seconds)
        inspected += 1
        response = client.get(
            API_URL.format(barcode=barcode),
            params={"fields": "code,product_name,brands,quantity,selected_images,photographers_tags", "product_type": "all"},
            headers={"User-Agent": user_agent, "Accept": "application/json"},
            timeout=(5, 25),
        )
        if response.status_code == 404:
            # A catalogue GTIN may not exist in the community-maintained source.
            # Treat that as a normal no-match and continue with the remaining products.
            print(f"SKIP {barcode}: no Open Facts product record for this barcode")
            continue
        if response.status_code in (429, 503):
            print("Open Facts rate limit/service unavailable; stop and retry later.", file=sys.stderr)
            return 3
        response.raise_for_status()
        candidate, skip_reason = explain_candidate(item, response.json(), response.url)
        if not candidate:
            print(f"SKIP {barcode}: {skip_reason}")
            continue
        candidates += 1
        local_name = str(item.get("name") or "(name not listed)")
        local_brand = str(item.get("brand") or "(brand not listed)")
        local_pack = str(item.get("packSize") or "(pack size not listed)")
        print(f"CANDIDATE product #{item.get('productId')} · GTIN {barcode}")
        print(f"  Apniko : {local_name} · {local_brand} · {local_pack}")
        print(f"  Source : {candidate['sourceProductName']} · {candidate['sourceProductBrand']} · {candidate['sourcePackSize']}")
        print(f"  Image  : {candidate['imageUrl']}")
        if args.apply:
            saved = client.post(
                base + f"/api/admin/catalogue/products/{item['productId']}/photo-candidate",
                json=candidate, headers=headers, timeout=(5, 20),
            )
            saved.raise_for_status()
            submitted += 1
            print(f"  submitted product #{item['productId']} for admin review")

    print(f"Inspected: {inspected}; candidates: {candidates}; submitted: {submitted}")
    if not args.apply:
        print("Dry run only. Review the candidate list before re-running with --apply.")
    return 0


def main() -> int:
    try:
        return run(parser().parse_args())
    except requests.RequestException as error:
        print(f"Import request failed: {error}", file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
