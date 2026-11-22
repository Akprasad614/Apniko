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
from io import BytesIO
from urllib.parse import urlparse, urlunparse

import requests
from PIL import Image, ImageFilter, ImageOps, ImageStat, UnidentifiedImageError

API_URL = "https://world.openfoodfacts.org/api/v3/product/{barcode}.json"
LICENSE = "CC BY-SA"
SOURCES = {
    "world.openfoodfacts.org": "Open Food Facts",
    "world.openbeautyfacts.org": "Open Beauty Facts",
    "world.openpetfoodfacts.org": "Open Pet Food Facts",
    "world.openproductsfacts.org": "Open Products Facts",
}
IMAGE_HOSTS = {"images." + host.removeprefix("world.") for host in SOURCES}
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}
MAX_IMAGE_BYTES = 12 * 1024 * 1024
MIN_IMAGE_SIDE = 600
PHOTO_POLICY_VERSION = "india-market-clean-background-multi-source-display-v4"


def get_with_retries(
    session: requests.Session,
    url: str,
    *,
    max_retries: int = 3,
    **kwargs,
) -> requests.Response:
    """Retry temporary network/5xx failures without hiding persistent errors."""
    last_response = None
    for attempt in range(max_retries + 1):
        try:
            response = session.get(url, **kwargs)
        except (requests.Timeout, requests.ConnectionError):
            if attempt >= max_retries:
                raise
            time.sleep(min(2 ** attempt, 30))
            continue

        if response.status_code not in RETRYABLE_STATUS_CODES:
            return response
        last_response = response
        if attempt >= max_retries:
            return response

        retry_after = response.headers.get("Retry-After") if hasattr(response, "headers") else None
        try:
            wait_seconds = float(retry_after) if retry_after else float(2 ** attempt)
        except (TypeError, ValueError):
            wait_seconds = float(2 ** attempt)
        time.sleep(min(max(wait_seconds, 1.0), 60.0))

    if last_response is not None:
        return last_response
    raise requests.RequestException("Request failed after retry policy was exhausted")


def _corner_is_clean_background(image: Image.Image, corner: str) -> bool:
    width, height = image.size
    patch_width = max(8, int(width * 0.05))
    patch_height = max(8, int(height * 0.05))
    boxes = {
        "top-left": (0, 0, patch_width, patch_height),
        "top-right": (width - patch_width, 0, width, patch_height),
        "bottom-left": (0, height - patch_height, patch_width, height),
        "bottom-right": (width - patch_width, height - patch_height, width, height),
    }
    patch = image.crop(boxes[corner]).resize((32, 32)).convert("RGBA")
    pixels = list(patch.getdata())
    transparent = sum(1 for r, g, b, a in pixels if a <= 16) / len(pixels)
    if transparent >= 0.85:
        return True
    neutral_white = sum(
        1 for r, g, b, a in pixels
        if a >= 240 and min(r, g, b) >= 228 and max(r, g, b) - min(r, g, b) <= 28
    ) / len(pixels)
    return neutral_white >= 0.85


def inspect_image_quality(
    session: requests.Session,
    image_url: str,
    barcode: str,
    *,
    max_retries: int = 3,
) -> tuple[dict | None, str | None]:
    """Conservatively screen the actual image bytes before it enters moderation.

    This is a basic technical/background gate, not a substitute for human review.
    """
    if not safe_image_url(image_url, barcode):
        return None, "image URL is not an HTTPS image for the matching barcode"

    response = get_with_retries(
        session,
        image_url,
        headers={"User-Agent": "ApnikoProductPhotoImporter/2.0", "Accept": "image/jpeg,image/png,image/webp"},
        timeout=(5, 30),
        max_retries=max_retries,
    )
    if response.status_code == 404:
        return None, "full-size image is unavailable"
    response.raise_for_status()

    content_type = str(response.headers.get("Content-Type", "")).split(";", 1)[0].strip().lower()
    if content_type not in {"image/jpeg", "image/png", "image/webp"}:
        return None, f"unsupported image content type: {content_type or 'missing'}"
    content = response.content
    if not content:
        return None, "image response is empty"
    if len(content) > MAX_IMAGE_BYTES:
        return None, "image exceeds the 12 MB safety limit"

    try:
        with Image.open(BytesIO(content)) as opened:
            opened.verify()
        with Image.open(BytesIO(content)) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGBA")
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        return None, "image bytes are invalid or unsafe to decode"

    width, height = image.size
    if min(width, height) < MIN_IMAGE_SIDE:
        return None, f"image resolution is too small ({width}x{height}; minimum short side {MIN_IMAGE_SIDE}px)"

    clean_corners = sum(
        1 for corner in ("top-left", "top-right", "bottom-left", "bottom-right")
        if _corner_is_clean_background(image, corner)
    )
    if clean_corners < 3:
        return None, f"background is not sufficiently clean/neutral ({clean_corners}/4 clean corners)"

    preview = image.convert("RGB")
    preview.thumbnail((256, 256))
    edges = preview.convert("L").filter(ImageFilter.FIND_EDGES)
    sharpness_score = float(ImageStat.Stat(edges).var[0])
    if sharpness_score < 8.0:
        return None, f"image appears blurred or lacks detail (edge variance {sharpness_score:.1f})"

    return {
        "width": width,
        "height": height,
        "bytes": len(content),
        "cleanBackgroundCorners": clean_corners,
        "sharpnessScore": round(sharpness_score, 1),
    }, None


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


def valid_gtin(value: str) -> bool:
    """Return whether value is an 8, 12, 13 or 14 digit GTIN with a valid check digit."""
    code = str(value or "").strip()
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code):
        return False
    total = 0
    weight = 3
    for digit in reversed(code[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def explain_candidate(
    local_product: dict, payload: dict, api_url: str
) -> tuple[dict | None, str | None]:
    barcode = str(local_product.get("barcode") or "").strip()
    product = payload.get("product") or {}
    if not barcode:
        return None, "Apniko product has no barcode"
    if not valid_gtin(barcode):
        return None, "Apniko barcode is not a valid GTIN check digit"
    if str(product.get("code") or "").strip() != barcode:
        return None, "source GTIN does not exactly match the Apniko barcode"

    source_host = urlparse(api_url).hostname
    source_name = SOURCES.get(source_host or "")
    if not source_name:
        return None, "source catalogue is not allowlisted"

    country_tags = {
        str(tag).strip().casefold()
        for tag in (product.get("countries_tags") or [])
        if str(tag).strip()
    }
    country_names = {
        re.sub(r"[^a-z]", "", token.casefold())
        for token in re.split(r"[,;]", str(product.get("countries") or ""))
        if token.strip()
    }
    if "en:india" not in country_tags and "india" not in country_names:
        return None, "Open Facts does not tag India as a country where this product is sold"

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
    display_variants = front.get("display") or front.get("small") or {}
    display_image_url = (
        display_variants.get("en") or next(iter(display_variants.values()), None)
        if isinstance(display_variants, dict)
        else None
    )
    if not display_image_url:
        full_variants = front.get("full") or {}
        display_image_url = (
            full_variants.get("en") or next(iter(full_variants.values()), None)
            if isinstance(full_variants, dict)
            else full_variants if isinstance(full_variants, str) else None
        )
    if not display_image_url:
        return None, "source record has no front photograph"

    # Inspect the full-resolution original, but submit the display derivative to Apniko
    # so mobile product cards do not fetch huge originals.
    full_variants = front.get("full") or {}
    quality_image_url = (
        full_variants.get("en") or next(iter(full_variants.values()), None)
        if isinstance(full_variants, dict)
        else full_variants if isinstance(full_variants, str) else None
    )
    if not quality_image_url:
        parsed_image = urlparse(display_image_url)
        full_path = re.sub(
            r"\.(?:100|200|400)\.(?=(?:jpg|jpeg|png|webp)$)",
            ".full.",
            parsed_image.path,
            flags=re.IGNORECASE,
        )
        quality_image_url = (
            urlunparse(parsed_image._replace(path=full_path))
            if full_path != parsed_image.path
            else display_image_url
        )

    if not safe_image_url(display_image_url, barcode):
        return None, "display photograph URL is not on the matching allowlisted image host"
    if not safe_image_url(quality_image_url, barcode):
        return None, "full-size quality-check URL is not on the matching allowlisted image host"

    photographers = product.get("photographers_tags") or []
    photographer_names = ", ".join(str(name) for name in photographers[:8] if str(name).strip())
    attribution = "Open Facts contributors"
    if photographer_names:
        attribution += "; photographers: " + photographer_names
    return {
        "imageUrl": display_image_url,
        "qualityImageUrl": quality_image_url,
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
    result.add_argument("--max-retries", type=int, default=3)
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
    max_retries = getattr(args, "max_retries", 3)
    if not 1 <= args.limit <= 100 or args.page < 0 or args.delay_seconds < 4.0 or not 0 <= max_retries <= 6:
        print("Use --limit 1..100, non-negative --page, --delay-seconds >= 4 and --max-retries 0..6.", file=sys.stderr)
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
        response = get_with_retries(
            client,
            base + "/api/admin/catalogue/photo-import-targets",
            params={"page": page, "size": min(100, args.limit)},
            headers=headers,
            timeout=(5, 20),
            max_retries=max_retries,
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
        response = get_with_retries(
            client,
            API_URL.format(barcode=barcode),
            params={"fields": "code,product_name,brands,quantity,countries,countries_tags,selected_images,photographers_tags", "product_type": "all"},
            headers={"User-Agent": user_agent, "Accept": "application/json"},
            timeout=(5, 25),
            max_retries=max_retries,
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

        quality, quality_reason = inspect_image_quality(
            client, candidate["qualityImageUrl"], barcode, max_retries=max_retries
        )
        if not quality:
            print(f"SKIP {barcode}: image-quality check failed: {quality_reason}")
            continue
        candidates += 1
        local_name = str(item.get("name") or "(name not listed)")
        local_brand = str(item.get("brand") or "(brand not listed)")
        local_pack = str(item.get("packSize") or "(pack size not listed)")
        print(f"CANDIDATE product #{item.get('productId')} · GTIN {barcode}")
        print(f"  Apniko : {local_name} · {local_brand} · {local_pack}")
        print(f"  Source : {candidate['sourceProductName']} · {candidate['sourceProductBrand']} · {candidate['sourcePackSize']}")
        print(f"  Display image : {candidate['imageUrl']}")
        print(f"  Quality source: {candidate['qualityImageUrl']}")
        print(
            f"  Quality: {quality['width']}x{quality['height']} px, "
            f"{quality['bytes']} bytes, {quality['cleanBackgroundCorners']}/4 clean corners, "
            f"edge variance {quality['sharpnessScore']}"
        )
        print("  Market : India country tag present; current availability still needs administrator verification.")
        if args.apply:
            submission = {
                key: value for key, value in candidate.items() if key != "qualityImageUrl"
            }
            saved = client.post(
                base + f"/api/admin/catalogue/products/{item['productId']}/photo-candidate",
                json=submission, headers=headers, timeout=(5, 20),
            )
            if saved.status_code == 409:
                print(f"  SKIP product #{item['productId']}: this exact image URL was already rejected or retired")
                continue
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
