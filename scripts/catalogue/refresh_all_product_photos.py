#!/usr/bin/env python3
"""Resumable India-market-screened product photo refresh for Apniko.

The default mode is dry-run. --apply submits candidates as PENDING only; it
never approves or publishes images. The Open Food Facts India country tag is
a screening signal, not proof of current retail availability. An administrator
must independently verify current Indian-market availability and image rights.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
import sys
import time
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse

import requests

from import_product_photos import (
    PHOTO_POLICY_VERSION,
    SOURCES,
    explain_candidate,
    get_with_retries,
    inspect_image_quality,
    valid_gtin,
)

PAGE_SIZE = 100
DEFAULT_DELAY = 4.1
USER_AGENT = "ApnikoProductPhotoImporter/2.0 (https://github.com/Akprasad614/Apniko)"
CHECKPOINT = Path.home() / ".cache" / "apniko-photo-refresh" / "processed.json"


def fingerprint(item: dict) -> str:
    identity = {
        "policyVersion": PHOTO_POLICY_VERSION,
        "productId": item.get("productId"),
        "barcode": str(item.get("barcode") or "").strip(),
        "name": str(item.get("name") or "").strip(),
        "brand": str(item.get("brand") or "").strip(),
        "packSize": str(item.get("packSize") or "").strip(),
    }
    raw = json.dumps(identity, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def load_checkpoint() -> dict[str, str]:
    if not CHECKPOINT.exists():
        return {}
    try:
        value = json.loads(CHECKPOINT.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"Cannot read refresh checkpoint {CHECKPOINT}: {exc}") from exc
    if not isinstance(value, dict) or not all(
        isinstance(key, str) and isinstance(item, str) for key, item in value.items()
    ):
        raise RuntimeError(f"Refresh checkpoint has an unexpected format: {CHECKPOINT}")
    return value


def save_checkpoint(state: dict[str, str]) -> None:
    CHECKPOINT.parent.mkdir(parents=True, exist_ok=True)
    temporary = CHECKPOINT.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(state, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(CHECKPOINT)


def login(session: requests.Session, base: str) -> dict[str, str]:
    email = input("Administrator email: ").strip()
    if not email:
        raise RuntimeError("Administrator email is required.")
    password = getpass.getpass("Administrator password: ")
    if not password:
        raise RuntimeError("Administrator password is required.")
    try:
        response = session.post(
            base + "/api/auth/login",
            json={"email": email, "password": password},
            headers={"Accept": "application/json"},
            timeout=(5, 20),
        )
    finally:
        password = ""
    response.raise_for_status()
    payload = response.json()
    if payload.get("role") != "ADMIN" or not payload.get("token"):
        raise RuntimeError("Login did not return an authenticated ADMIN session.")
    return {"Authorization": "Bearer " + payload["token"], "Accept": "application/json"}


def get_report(
    session: requests.Session,
    base: str,
    headers: dict[str, str],
    max_retries: int,
) -> dict:
    response = get_with_retries(
        session,
        base + "/api/admin/catalogue/photo-rollout-report",
        headers=headers,
        timeout=(5, 20),
        max_retries=max_retries,
    )
    response.raise_for_status()
    return response.json()


def get_all_targets(
    session: requests.Session,
    base: str,
    headers: dict[str, str],
    max_retries: int,
) -> list[dict]:
    """Snapshot every target before submitting anything, preventing page shifts."""
    targets: list[dict] = []
    page = 0
    while True:
        response = get_with_retries(
            session,
            base + "/api/admin/catalogue/photo-import-targets",
            params={"page": page, "size": PAGE_SIZE},
            headers=headers,
            timeout=(5, 30),
            max_retries=max_retries,
        )
        response.raise_for_status()
        body = response.json()
        content = body.get("content") or []
        targets.extend(content)
        if not content or body.get("last", False):
            break
        page += 1

    unique: dict[str, dict] = {}
    for item in targets:
        if item.get("productId") is not None:
            unique[str(item["productId"])] = item
    return list(unique.values())


def metadata_coverage(targets: list[dict]) -> dict[str, int]:
    """Count metadata needed for reliable exact product-photo matching."""
    total = len(targets)
    with_brand = sum(bool(str(item.get("brand") or "").strip()) for item in targets)
    with_pack = sum(bool(str(item.get("packSize") or "").strip()) for item in targets)
    with_valid_gtin = sum(
        valid_gtin(str(item.get("barcode") or "").strip()) for item in targets
    )
    return {
        "total": total,
        "with_valid_gtin": with_valid_gtin,
        "with_brand": with_brand,
        "missing_brand": total - with_brand,
        "with_pack_size": with_pack,
        "missing_pack_size": total - with_pack,
        "with_brand_and_pack_size": sum(
            bool(str(item.get("brand") or "").strip())
            and bool(str(item.get("packSize") or "").strip())
            for item in targets
        ),
    }


def has_strict_match_metadata(item: dict) -> bool:
    return (
        valid_gtin(str(item.get("barcode") or "").strip())
        and bool(str(item.get("brand") or "").strip())
        and bool(str(item.get("packSize") or "").strip())
    )


def prioritize_targets(targets: list[dict]) -> list[dict]:
    """Inspect matchable packaged products first; retain incomplete rows for audit."""
    return sorted(
        targets,
        key=lambda item: (
            not has_strict_match_metadata(item),
            int(item.get("productId") or 0),
        ),
    )


def print_metadata_coverage(targets: list[dict]) -> None:
    summary = metadata_coverage(targets)
    print("\nPHOTO-SOURCE METADATA COVERAGE")
    print(f"  Eligible targets: {summary['total']}")
    print(f"  Valid GTIN: {summary['with_valid_gtin']}")
    print(f"  Brand present: {summary['with_brand']}; missing: {summary['missing_brand']}")
    print(
        f"  Pack size present: {summary['with_pack_size']}; "
        f"missing: {summary['missing_pack_size']}"
    )
    print(
        "  Have both brand and pack size for strict packaged-product matching: "
        f"{summary['with_brand_and_pack_size']}"
    )

    incomplete = [
        item for item in targets
        if not str(item.get("brand") or "").strip()
        or not str(item.get("packSize") or "").strip()
    ]
    if incomplete:
        print("  Examples needing another source or catalogue metadata completion:")
        for item in incomplete[:12]:
            missing = []
            if not str(item.get("brand") or "").strip():
                missing.append("brand")
            if not str(item.get("packSize") or "").strip():
                missing.append("pack size")
            name = str(item.get("name") or "(unnamed)")[:70]
            print(
                f"    product #{item.get('productId')} · {name} "
                f"(missing {', '.join(missing)})"
            )


def lookup_candidate_across_sources(
    session: requests.Session,
    item: dict,
    barcode: str,
    *,
    max_retries: int,
) -> tuple[dict | None, dict | None, str | None, bool]:
    """Use the universal barcode endpoint and follow its family-database redirect.

    An exact barcode match must still pass the India tag, brand, pack-size,
    image URL and image-byte quality checks before it can become a candidate.
    """
    # Open Facts supports a universal barcode lookup. product_type=all redirects
    # to the matching Food, Beauty, Pet Food or Products Facts instance.
    lookup_url = f"https://world.openfoodfacts.org/api/v3/product/{barcode}.json"
    response = get_with_retries(
        session,
        lookup_url,
        params={
            "product_type": "all",
            "fields": "code,product_name,brands,quantity,countries,countries_tags,selected_images,photographers_tags",
        },
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        timeout=(5, 25),
        max_retries=max_retries,
    )

    source_host = urlparse(response.url or lookup_url).hostname or "world.openfoodfacts.org"
    if response.status_code == 404:
        return None, None, "no record in any Open Facts family database", False
    if response.status_code in (429, 503):
        return None, None, (
            f"{source_host}: rate limit/service unavailable after retries"
        ), True

    response.raise_for_status()
    candidate, reason = explain_candidate(item, response.json(), response.url)
    if not candidate:
        return None, None, f"{source_host}: {reason}", False

    quality, quality_reason = inspect_image_quality(
        session,
        candidate["qualityImageUrl"],
        barcode,
        max_retries=max_retries,
    )
    if not quality:
        return None, None, (
            f"{source_host}: image-quality check failed: {quality_reason}"
        ), False

    return candidate, quality, None, False


def format_report(label: str, report: dict) -> None:
    print(f"\n{label}")
    for key, title in (
        ("totalCatalogueProducts", "Catalogue products"),
        ("productsWithBarcodeField", "Products with barcode field"),
        ("productsWithApprovedPhoto", "Products with approved photos"),
        ("pendingCandidates", "Pending photo candidates"),
        ("productsEligibleForFreshLookup", "Eligible for fresh lookup"),
        ("retiredCandidates", "Retired candidates (audited)"),
    ):
        if key in report:
            print(f"  {title}: {report[key]}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--api-url",
        default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"),
        help="Local Apniko backend URL (default: http://localhost:8080)",
    )
    parser.add_argument("--delay-seconds", type=float, default=DEFAULT_DELAY)
    parser.add_argument(
        "--max-retries",
        type=int,
        default=3,
        help="Retries for temporary network errors and rate limits (0..6)",
    )
    parser.add_argument(
        "--max-products",
        type=int,
        default=0,
        help="Maximum targets to inspect; 0 means all eligible products",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Submit verified candidates to the pending administrator queue",
    )
    parser.add_argument(
        "--audit-only",
        action="store_true",
        help="Report catalogue metadata coverage without querying external photo sources",
    )
    return parser.parse_args()


def main() -> int:
    """Prevent execution of the retired Open Facts bulk photo importer."""
    print(
        "Open Facts photo refresh is disabled for Apniko. "
        "Use only manufacturer/supplier catalogues with written image permission.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
