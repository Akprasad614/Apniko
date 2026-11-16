#!/usr/bin/env python3
"""Dry-run-first, exact-GTIN product photo candidate importer for Apniko."""
from __future__ import annotations

import argparse
import os
import re
import sys
import time
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


def normalize_identity_value(value: object) -> str:
    return re.sub(r"\\s+", " ", str(value or "").strip()).casefold()


def find_candidate(local_product: dict, payload: dict, api_url: str) -> dict | None:
    barcode = str(local_product.get("barcode") or "").strip()
    product = payload.get("product") or {}
    if not barcode or str(product.get("code") or "").strip() != barcode:
        return None
    source_host = urlparse(api_url).hostname
    source_name = SOURCES.get(source_host or "")
    if not source_name:
        return None

    source_brand = str(product.get("brands") or "").split(",")[0].strip()
    source_pack_size = str(product.get("quantity") or "").strip()
    local_brand = str(local_product.get("brand") or "").strip()
    local_pack_size = str(local_product.get("packSize") or "").strip()
    # A GTIN match alone can still point to incomplete or inconsistent community data.
    # Do not offer a candidate if the source's brand or pack size is absent or different.
    if (not source_brand or not source_pack_size
            or not normalize_identity_value(local_brand)
            or not normalize_identity_value(local_pack_size)
            or normalize_identity_value(source_brand) != normalize_identity_value(local_brand)
            or normalize_identity_value(source_pack_size) != normalize_identity_value(local_pack_size)):
        return None

    front = (product.get("selected_images") or {}).get("front") or {}
    variants = front.get("display") or front.get("small") or {}
    image_url = variants.get("en") or next(iter(variants.values()), None)
    if not safe_image_url(image_url, barcode):
        return None

    photographers = product.get("photographers_tags") or []
    photographer_names = ", ".join(str(name) for name in photographers[:8] if str(name).strip())
    attribution = "Open Facts contributors"
    if photographer_names:
        attribution += "; photographers: " + photographer_names
    return {
        "imageUrl": image_url,
        "sourceUrl": f"https://{source_host}/product/{barcode}",
        "sourceName": source_name,
        "sourceProductName": str(product.get("product_name") or local_product.get("name") or "").strip(),
        "sourceProductBrand": source_brand,
        "sourcePackSize": source_pack_size,
        "license": LICENSE,
        "attribution": attribution[:500],
        "authorizationReference": None,
        "matchedBarcode": barcode,
    }


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    result.add_argument("--limit", type=int, default=10)
    result.add_argument("--page", type=int, default=0)
    result.add_argument("--delay-seconds", type=float, default=4.1)
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
        candidate = find_candidate(item, response.json(), response.url)
        if not candidate:
            print(f"SKIP {barcode}: no exact-barcode front photograph")
            continue
        candidates += 1
        print(f"CANDIDATE {barcode}: {candidate['sourceProductName']} — {candidate['imageUrl']}")
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
