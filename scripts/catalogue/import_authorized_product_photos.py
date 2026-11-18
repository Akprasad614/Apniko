#!/usr/bin/env python3
"""Dry-run-first importer for manufacturer/supplier-authorised Apniko product photos.

Expected CSV columns:
barcode,source_product_name,source_brand,source_pack_size,image_url,source_url,
authorization_reference,attribution

The script never downloads or copies image bytes. It submits HTTPS image references
to Apniko's admin review queue only when --apply is explicitly provided.
"""
from __future__ import annotations

import argparse
import csv
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import requests

SOURCE_NAME = "Brand / supplier authorised catalogue"
LICENSE = "MANUFACTURER_AUTHORIZED"
REQUIRED_COLUMNS = {
    "barcode",
    "source_product_name",
    "source_brand",
    "source_pack_size",
    "image_url",
    "source_url",
    "authorization_reference",
    "attribution",
}


def clean(value: object) -> str:
    return str(value or "").strip()


def normalize(value: object) -> str:
    return re.sub(r"\s+", " ", clean(value)).casefold()


def valid_gtin(value: str) -> bool:
    code = clean(value)
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code):
        return False
    total = 0
    weight = 3
    for digit in reversed(code[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def valid_https_url(value: str) -> bool:
    try:
        parsed = urlparse(value)
        return (
            parsed.scheme == "https"
            and bool(parsed.hostname)
            and parsed.username is None
            and parsed.password is None
            and parsed.port in (None, 443)
            and not parsed.query
            and not parsed.fragment
        )
    except ValueError:
        return False


def allowed_image_host(image_url: str, source_url: str, configured_hosts: set[str]) -> bool:
    image_host = (urlparse(image_url).hostname or "").lower()
    source_host = (urlparse(source_url).hostname or "").lower()
    if image_host == source_host or image_host.endswith("." + source_host):
        return True
    return image_host in configured_hosts


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--file", required=True, type=Path, help="Authorised supplier CSV file")
    result.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    result.add_argument("--limit", type=int, default=100)
    result.add_argument("--apply", action="store_true", help="Submit valid candidates to the admin review queue")
    return result


def load_targets(client: requests.Session, base: str, headers: dict[str, str]) -> dict[str, dict]:
    targets: dict[str, dict] = {}
    page = 0
    while True:
        response = client.get(
            base + "/api/admin/catalogue/photo-import-targets",
            params={"page": page, "size": 100},
            headers=headers,
            timeout=(5, 20),
        )
        response.raise_for_status()
        body = response.json()
        for item in body.get("content") or []:
            barcode = clean(item.get("barcode"))
            if barcode:
                targets[barcode] = item
        if body.get("last", True) or not body.get("content"):
            return targets
        page += 1


def run(args: argparse.Namespace, session: requests.Session | None = None) -> int:
    token = os.environ.get("APNIKO_ADMIN_TOKEN", "").strip()
    if not token:
        print("Set APNIKO_ADMIN_TOKEN to an active administrator JWT.", file=sys.stderr)
        return 2
    if not 1 <= args.limit <= 500:
        print("Use --limit 1..500.", file=sys.stderr)
        return 2
    if not args.file.is_file():
        print(f"CSV file not found: {args.file}", file=sys.stderr)
        return 2

    base = args.api_url.rstrip("/")
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    configured_hosts = {
        host.strip().lower()
        for host in os.environ.get("APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS", "").split(",")
        if host.strip()
    }
    client = session or requests.Session()
    print("Mode:", "APPLY" if args.apply else "DRY RUN")
    targets = load_targets(client, base, headers)

    inspected = ready = submitted = skipped = 0
    try:
        with args.file.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            columns = set(reader.fieldnames or [])
            missing = REQUIRED_COLUMNS - columns
            if missing:
                print("CSV is missing required columns: " + ", ".join(sorted(missing)), file=sys.stderr)
                return 2

            for row_number, row in enumerate(reader, start=2):
                if inspected >= args.limit:
                    break
                inspected += 1
                barcode = clean(row.get("barcode"))
                if not valid_gtin(barcode):
                    print(f"SKIP row {row_number}: invalid GTIN")
                    skipped += 1
                    continue
                local = targets.get(barcode)
                if not local:
                    print(f"SKIP {barcode}: no matching Apniko product is waiting for a photo")
                    skipped += 1
                    continue

                source_name = clean(row.get("source_product_name"))
                source_brand = clean(row.get("source_brand"))
                source_pack = clean(row.get("source_pack_size"))
                local_brand = clean(local.get("brand"))
                local_pack = clean(local.get("packSize"))
                image_url = clean(row.get("image_url"))
                source_url = clean(row.get("source_url"))
                permission = clean(row.get("authorization_reference"))
                attribution = clean(row.get("attribution"))

                missing_values = [
                    label for label, value in (
                        ("source product name", source_name),
                        ("source brand", source_brand),
                        ("source pack size", source_pack),
                        ("permission reference", permission),
                        ("attribution", attribution),
                    ) if not value
                ]
                if missing_values:
                    print(f"SKIP {barcode}: missing " + ", ".join(missing_values))
                    skipped += 1
                    continue
                if not local_brand or normalize(source_brand) != normalize(local_brand):
                    print(f"SKIP {barcode}: source brand does not exactly match Apniko ({local_brand or 'not recorded'})")
                    skipped += 1
                    continue
                if not local_pack or normalize(source_pack) != normalize(local_pack):
                    print(f"SKIP {barcode}: source pack size does not exactly match Apniko ({local_pack or 'not recorded'})")
                    skipped += 1
                    continue
                if not valid_https_url(source_url) or not valid_https_url(image_url):
                    print(f"SKIP {barcode}: source and image URLs must be HTTPS without query strings or fragments")
                    skipped += 1
                    continue
                if not allowed_image_host(image_url, source_url, configured_hosts):
                    print(f"SKIP {barcode}: image host is not the source domain or a configured authorised CDN")
                    skipped += 1
                    continue

                candidate = {
                    "imageUrl": image_url,
                    "sourceUrl": source_url,
                    "sourceName": SOURCE_NAME,
                    "sourceProductName": source_name,
                    "sourceProductBrand": source_brand,
                    "sourcePackSize": source_pack,
                    "license": LICENSE,
                    "attribution": attribution[:500],
                    "authorizationReference": permission[:500],
                    "matchedBarcode": barcode,
                }
                ready += 1
                print(
                    f"READY {barcode}: {source_brand} / {source_name} / {source_pack} "
                    f"→ Apniko product #{local['productId']}"
                )
                if args.apply:
                    response = client.post(
                        base + f"/api/admin/catalogue/products/{local['productId']}/photo-candidate",
                        json=candidate,
                        headers=headers,
                        timeout=(5, 20),
                    )
                    response.raise_for_status()
                    submitted += 1
                    print(f"  submitted product #{local['productId']} for admin review")
    except (OSError, UnicodeError, csv.Error) as error:
        print(f"Unable to read supplier CSV: {error}", file=sys.stderr)
        return 4
    except requests.RequestException as error:
        print(f"Import request failed: {error}", file=sys.stderr)
        return 4

    print(f"Inspected: {inspected}; ready: {ready}; skipped: {skipped}; submitted: {submitted}")
    if not args.apply:
        print("Dry run only. Check every supplier permission and pack match before re-running with --apply.")
    return 0


def main() -> int:
    return run(parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
